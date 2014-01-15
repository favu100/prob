package de.prob.eventb.disprover.core.internal;

import java.util.Set;

import org.eclipse.core.runtime.jobs.Job;
import org.eventb.core.ast.Predicate;
import org.eventb.core.seqprover.IProofMonitor;

import de.be4.classicalb.core.parser.analysis.prolog.ASTProlog;
import de.be4.classicalb.core.parser.node.AEventBContextParseUnit;
import de.prob.core.*;
import de.prob.core.command.*;
import de.prob.eventb.disprover.core.command.DisproverLoadCommand;
import de.prob.eventb.translator.internal.TranslationVisitor;
import de.prob.exceptions.ProBException;
import de.prob.parser.ISimplifiedROMap;
import de.prob.prolog.output.IPrologTermOutput;
import de.prob.prolog.term.*;

/**
 * The DisproverCommand takes two sets of ASTs (one for the machine and a list
 * for the contexts) and tries to set them up with ProB. If setup is possible,
 * the arguments from that operation are joined with the provided variables and
 * returned as an {@link ICounterExample}.
 * <p>
 * 
 * This command is probably not useful without {@link DisproverReasoner}, which
 * calls it.
 * 
 * @author jastram
 */
public class DisproverCommand implements IComposableCommand {

	private static final String RESULT = "Result";

	private CounterExample counterExample;
	private final Set<Predicate> hypotheses;
	private final Predicate goal;
	private final int timeoutFactor;

	private static ComposedCommand composed;

	public DisproverCommand(Set<Predicate> hypotheses, Predicate goal,
			int timeoutFactor) {
		this.hypotheses = hypotheses;
		this.goal = goal;
		this.timeoutFactor = timeoutFactor;
	}

	public static ICounterExample disprove(Animator animator,
			Set<Predicate> hypotheses, Predicate goal, int timeoutFactor,
			AEventBContextParseUnit context, IProofMonitor pm)
			throws ProBException, InterruptedException {

		final ClearMachineCommand clear = new ClearMachineCommand();
		final SetPreferencesCommand setPrefs = SetPreferencesCommand
				.createSetPreferencesCommand(animator);

		DisproverLoadCommand load = new DisproverLoadCommand(context);

		StartAnimationCommand start = new StartAnimationCommand();

		DisproverCommand disprove = new DisproverCommand(hypotheses, goal,
				timeoutFactor);

		composed = new ComposedCommand(clear, setPrefs, load, start, disprove);

		final Job job = new ProBCommandJob("Disproving", animator, composed);
		job.setUser(true);
		job.schedule();

		while (job.getResult() == null && !pm.isCanceled()) {
			Thread.sleep(200);
		}

		if (pm.isCanceled()) {
			job.cancel();
			throw new InterruptedException();
		}

		return disprove.getResult();

	}

	private ICounterExample getResult() {
		return counterExample;
	}

	@Override
	public void writeCommand(final IPrologTermOutput pto) {
		pto.openTerm("cbc_disprove");

		Predicate pred = goal;
		translatePredicate(pto, pred);
		pto.openList();
		for (Predicate p : this.hypotheses) {
			translatePredicate(pto, p);
		}
		pto.closeList();
		pto.printNumber(timeoutFactor);
		pto.printVariable(RESULT);
		pto.closeTerm();
	}

	private void translatePredicate(final IPrologTermOutput pto, Predicate pred) {
		TranslationVisitor v = new TranslationVisitor();
		pred.accept(v);
		ASTProlog p = new ASTProlog(pto, null);
		v.getPredicate().apply(p);
	}

	@Override
	public void processResult(
			final ISimplifiedROMap<String, PrologTerm> bindings)
			throws CommandException {

		PrologTerm term = bindings.get(RESULT);

		counterExample = null;

		if ("time_out".equals(term.getFunctor())) {
			counterExample = new CounterExample(false, true);
		}
		if ("interrupted".equals(term.getFunctor())) {
			throw new CommandException("Interrupted");
		}
		if ("no_solution_found".equals(term.getFunctor())) {
			PrologTerm reason = term.getArgument(1);
			if (reason.hasFunctor("clpfd_overflow", 0)) {
				counterExample = new CounterExample(false, false,
						"CLPFD Integer Overflow");
			} else if (reason.hasFunctor("unfixed_deferred_sets", 0)) {
				counterExample = new CounterExample(false, false,
						"unfixed deferred sets in predicate");
			} else {
				counterExample = new CounterExample(false, false,
						reason.toString());
			}
		}

		if ("contradiction_found".equals(term.getFunctor())) {
			counterExample = new CounterExample(false, false);
			counterExample.setProof(true);
		}

		if ("solution".equals(term.getFunctor())) {
			counterExample = new CounterExample(true, false);
			ListPrologTerm vars = (ListPrologTerm) term.getArgument(1);

			for (PrologTerm e : vars) {
				counterExample.addVar(e.getArgument(1).getFunctor(), e
						.getArgument(3).getFunctor());
			}
		}

		if (counterExample == null) {
			throw new CommandException(
					"Internal Error in ProB. Unexpected result:"
							+ term.toString());
		}
	}

}
