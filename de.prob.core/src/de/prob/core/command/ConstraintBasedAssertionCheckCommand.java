/**
 * 
 */
package de.prob.core.command;

import de.prob.parser.ISimplifiedROMap;
import de.prob.prolog.output.IPrologTermOutput;
import de.prob.prolog.term.ListPrologTerm;
import de.prob.prolog.term.PrologTerm;

/**
 * This command makes ProB search for a deadlock with an optional predicate to
 * limit the search space.
 * 
 * @author plagge
 */
public class ConstraintBasedAssertionCheckCommand implements IComposableCommand {

	public static enum ResultType {
		TIMEOUT, COUNTER_EXAMPLE, NO_COUNTER_EXAMPLE
	};

	private static final String COMMAND_NAME = "cbc_static_assertion_violation_checking";
	private static final String RESULT_VARIABLE = "R";

	private ResultType result;
	private ListPrologTerm counterExampleState;

	public ConstraintBasedAssertionCheckCommand() {
	}

	public ResultType getResult() {
		return result;
	}

	public ListPrologTerm getCounterExampleState() {
		return counterExampleState;
	}

	@Override
	public void writeCommand(final IPrologTermOutput pto) {
		pto.openTerm(COMMAND_NAME);
		pto.printVariable(RESULT_VARIABLE);
		pto.closeTerm();
	}

	@Override
	public void processResult(
			final ISimplifiedROMap<String, PrologTerm> bindings)
			throws CommandException {
		final PrologTerm resultTerm = bindings.get(RESULT_VARIABLE);
		final ResultType result;
		if (resultTerm.hasFunctor("time_out", 0)) {
			result = ResultType.TIMEOUT;
		} else if (resultTerm.hasFunctor("no_counterexample_found", 0)) {
			result = ResultType.NO_COUNTER_EXAMPLE;
		} else if (resultTerm.hasFunctor("counterexample_found", 1)) {
			counterExampleState = (ListPrologTerm) resultTerm.getArgument(1);
			result = ResultType.COUNTER_EXAMPLE;
		} else
			throw new CommandException(
					"unexpected result from deadlock check: " + resultTerm);
		this.result = result;

	}
}
