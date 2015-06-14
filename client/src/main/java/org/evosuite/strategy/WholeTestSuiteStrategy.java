package org.evosuite.strategy;

import java.util.ArrayList;
import java.util.List;

import org.evosuite.Properties;
import org.evosuite.Properties.Criterion;
import org.evosuite.coverage.TestFitnessFactory;
import org.evosuite.result.TestGenerationResultBuilder;
import org.evosuite.rmi.ClientServices;
import org.evosuite.rmi.service.ClientState;
import org.evosuite.ga.FitnessFunction;
import org.evosuite.ga.metaheuristics.GeneticAlgorithm;
import org.evosuite.ga.stoppingconditions.MaxStatementsStoppingCondition;
import org.evosuite.statistics.RuntimeVariable;
import org.evosuite.testcase.execution.ExecutionTracer;
import org.evosuite.testcase.TestFitnessFunction;
import org.evosuite.testsuite.TestSuiteChromosome;
import org.evosuite.testsuite.TestSuiteFitnessFunction;
import org.evosuite.utils.ArrayUtil;
import org.evosuite.utils.LoggingUtils;
import org.evosuite.utils.Randomness;

public class WholeTestSuiteStrategy extends TestGenerationStrategy {

	@Override
	public TestSuiteChromosome generateTests() {
		// Set up search algorithm
		LoggingUtils.getEvoLogger().info("* Setting up search algorithm for whole suite generation");
		PropertiesSuiteGAFactory algorithmFactory = new PropertiesSuiteGAFactory();
		GeneticAlgorithm<TestSuiteChromosome> algorithm = algorithmFactory.getSearchAlgorithm();
		
		if(Properties.SERIALIZE_GA || Properties.CLIENT_ON_THREAD)
			TestGenerationResultBuilder.getInstance().setGeneticAlgorithm(algorithm);

		long startTime = System.currentTimeMillis() / 1000;

		// What's the search target
		List<TestSuiteFitnessFunction> fitnessFunctions = getFitnessFunctions();

		// TODO: Argh, generics.
		algorithm.addFitnessFunctions((List)fitnessFunctions);
//		for(TestSuiteFitnessFunction f : fitnessFunctions) 
//			algorithm.addFitnessFunction(f);

		// if (Properties.SHOW_PROGRESS && !logger.isInfoEnabled())
		algorithm.addListener(progressMonitor); // FIXME progressMonitor may cause
		// client hang if EvoSuite is
		// executed with -prefix!

		if (ArrayUtil.contains(Properties.CRITERION, Criterion.DEFUSE)
				|| ArrayUtil.contains(Properties.CRITERION, Criterion.ALLDEFS)
				|| ArrayUtil.contains(Properties.CRITERION, Criterion.STATEMENT)
				|| ArrayUtil.contains(Properties.CRITERION, Criterion.RHO)
				|| ArrayUtil.contains(Properties.CRITERION, Criterion.AMBIGUITY))
			ExecutionTracer.enableTraceCalls();

		// TODO: why it was only if "analyzing"???
		// if (analyzing)
		algorithm.resetStoppingConditions();

		List<TestFitnessFunction> goals = getGoals(true);
		
		/*
		 * Proceed with search if CRITERION=EXCEPTION, even if goals is empty
		 */
		TestSuiteChromosome testSuite = null;
		if (!(Properties.STOP_ZERO && goals.isEmpty()) || ArrayUtil.contains(Properties.CRITERION, Criterion.EXCEPTION)) {
			// Perform search
			LoggingUtils.getEvoLogger().info("* Using seed {}", Randomness.getSeed() );
			LoggingUtils.getEvoLogger().info("* Starting evolution");
			ClientServices.getInstance().getClientNode().changeState(ClientState.SEARCH);

			algorithm.generateSolution();
			// TODO: Refactor MOO!
			// bestSuites = (List<TestSuiteChromosome>) ga.getBestIndividuals();
			testSuite = (TestSuiteChromosome) algorithm.getBestIndividual();
		} else {
			zeroFitness.setFinished();
			testSuite = new TestSuiteChromosome();
			for (FitnessFunction<?> ff : testSuite.getFitnessValues().keySet()) {
				testSuite.setCoverage(ff, 1.0);
			}

		}

		long endTime = System.currentTimeMillis() / 1000;

		goals = getGoals(false); //recalculated now after the search, eg to handle exception fitness
        ClientServices.getInstance().getClientNode().trackOutputVariable(RuntimeVariable.Total_Goals, goals.size());
        
		// Newline after progress bar
		if (Properties.SHOW_PROGRESS)
			LoggingUtils.getEvoLogger().info("");
		
		String text = " statements, best individual has fitness: ";
		LoggingUtils.getEvoLogger().info("* Search finished after "
				+ (endTime - startTime)
				+ "s and "
				+ algorithm.getAge()
				+ " generations, "
				+ MaxStatementsStoppingCondition.getNumExecutedStatements()
				+ text
				+ testSuite.getFitness());
		// Search is finished, send statistics
		sendExecutionStatistics();

		return testSuite;
	}
	
    private List<TestFitnessFunction> getGoals(boolean verbose) {
        List<TestFitnessFactory<? extends TestFitnessFunction>> goalFactories = getFitnessFactories();
        List<TestFitnessFunction> goals = new ArrayList<>();

        if(goalFactories.size() == 1) {
                TestFitnessFactory<? extends TestFitnessFunction> factory = goalFactories.iterator().next();
                goals.addAll(factory.getCoverageGoals());

                if(verbose) {
                        LoggingUtils.getEvoLogger().info("* Total number of test goals: {}", factory.getCoverageGoals().size());
                }
        } else {
                if(verbose) {
                        LoggingUtils.getEvoLogger().info("* Total number of test goals: ");
                }

                for (TestFitnessFactory<? extends TestFitnessFunction> goalFactory : goalFactories) {
                        goals.addAll(goalFactory.getCoverageGoals());

                        if(verbose) {
                                LoggingUtils.getEvoLogger().info("  - " + goalFactory.getClass().getSimpleName().replace("CoverageFactory", "")
                                                + " " + goalFactory.getCoverageGoals().size());
                        }
                }
        }
        return goals;
}
}