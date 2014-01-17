package de.prob.eventb.disprover.core.internal;

import java.util.*;

import org.eclipse.core.runtime.Status;
import org.eventb.core.ast.Predicate;
import org.eventb.core.seqprover.*;
import org.eventb.core.seqprover.IProofRule.IAntecedent;
import org.rodinp.core.RodinDBException;

import de.be4.classicalb.core.parser.analysis.prolog.ASTProlog;
import de.be4.classicalb.core.parser.node.AEventBContextParseUnit;
import de.prob.core.*;
import de.prob.eventb.disprover.core.DisproverReasonerInput;
import de.prob.eventb.disprover.core.translation.DisproverContextCreator;
import de.prob.eventb.translator.internal.TranslationVisitor;
import de.prob.exceptions.ProBException;
import de.prob.logging.Logger;
import de.prob.prolog.output.PrologTermStringOutput;

public class DisproverReasoner implements IReasoner {

	static final String DISPROVER_CONTEXT = "disprover_context";

	private static final String DISPROVER_REASONER_NAME = "de.prob.eventb.disprover.core.disproverReasoner";

	private final int timeoutFactor;

	public DisproverReasoner(int timeoutFactor) {
		this.timeoutFactor = timeoutFactor;
	}

	@Override
	public String getReasonerID() {
		return DISPROVER_REASONER_NAME;
	}

	@Override
	public IReasonerOutput apply(final IProverSequent sequent,
			final IReasonerInput input, final IProofMonitor pm) {
		try {
			DisproverReasonerInput disproverInput = (DisproverReasonerInput) input;
			ICounterExample ce = evaluateSequent(sequent, disproverInput,
					timeoutFactor, pm);
			return createDisproverResult(ce, sequent, input);
		} catch (PrologException e) {
			Logger.log(Logger.WARNING, Status.WARNING, e.getMessage(), e);
			return ProverFactory.reasonerFailure(this, input, e.getMessage());
		} catch (ProBException e) {
			Logger.log(Logger.WARNING, Status.WARNING, e.getMessage(), e);
			return ProverFactory.reasonerFailure(this, input, e.getMessage());
		} catch (RodinDBException e) {
			Logger.log(Logger.WARNING, Status.WARNING, e.getMessage(), e);
			return ProverFactory.reasonerFailure(this, input, e.getMessage());
		} catch (InterruptedException e) {
			return ProverFactory.reasonerFailure(this, input, e.getMessage());

		}
	}

	private ICounterExample evaluateSequent(final IProverSequent sequent,
			final DisproverReasonerInput disproverInput, int timeoutFactor,
			IProofMonitor pm) throws ProBException, RodinDBException,
			InterruptedException {
		// Logger.info("Calling Disprover on Sequent");

		Set<Predicate> allHypotheses = new HashSet<Predicate>();
		// StringBuilder hypothesesString = new StringBuilder();
		for (Predicate predicate : sequent.hypIterable()) {
			allHypotheses.add(predicate);
			// hypothesesString.append(predicateToProlog(predicate));
			// hypothesesString.append(" & ");
		}

		Set<Predicate> selectedHypotheses = new HashSet<Predicate>();
		// StringBuilder hypothesesString = new StringBuilder();
		for (Predicate predicate : sequent.selectedHypIterable()) {
			selectedHypotheses.add(predicate);
			// hypothesesString.append(predicateToProlog(predicate));
			// hypothesesString.append(" & ");
		}

		/*
		 * if (hypothesesString.length() == 0) {
		 * Logger.info("Disprover: No Hypotheses"); } else {
		 * hypothesesString.delete(hypothesesString.length() - 2,
		 * hypothesesString.length());
		 * Logger.info("Disprover: Sending Hypotheses: " +
		 * UnicodeTranslator.toAscii(hypothesesString.toString())); }
		 */
		Predicate goal = sequent.goal();
		// Logger.info("Disprover: Sending Goal: "+
		// UnicodeTranslator.toAscii(predicateToProlog(goal)));

		AEventBContextParseUnit context = DisproverContextCreator
				.createDisproverContext(sequent);

		ICounterExample counterExample = DisproverCommand.disprove(
				Animator.getAnimator(), allHypotheses, selectedHypotheses,
				goal, timeoutFactor, context, pm);
		// Logger.info("Disprover: Result: " + counterExample.toString());

		return counterExample;
	}

	private String predicateToProlog(Predicate pred) {
		PrologTermStringOutput pto = new PrologTermStringOutput();
		TranslationVisitor v = new TranslationVisitor();
		pred.accept(v);
		ASTProlog p = new ASTProlog(pto, null);
		v.getPredicate().apply(p);
		return pto.toString();
	}

	/**
	 * Create a {@link IProofRule} containing the result from the disprover.
	 */
	private IReasonerOutput createDisproverResult(
			final ICounterExample counterExample, final IProverSequent sequent,
			final IReasonerInput input) {

		Predicate goal = sequent.goal();

		IAntecedent ante = ProverFactory.makeAntecedent(goal);

		if (counterExample.timeoutOccured())
			return ProverFactory.reasonerFailure(this, input,
					"ProB: Timeout occurred.");

		if (!counterExample.counterExampleFound() && counterExample.isProof())
			return ProverFactory.makeProofRule(this, input, sequent.goal(),
					null, IConfidence.DISCHARGED_MAX,
					"ProB (no enumeration / all cases checked)");

		if (!counterExample.counterExampleFound())
			return ProverFactory.reasonerFailure(
					this,
					input,
					"ProB: No Counter-Example found due to "
							+ counterExample.getReason()
							+ ", but there might exist one.");

		return ProverFactory.makeProofRule(this, input, null, null,
				IConfidence.PENDING, counterExample.toString(), ante);
	}

	@Override
	public IReasonerInput deserializeInput(final IReasonerInputReader reader)
			throws SerializeException {
		return new DisproverReasonerInput();
	}

	@Override
	public void serializeInput(final IReasonerInput input,
			final IReasonerInputWriter writer) throws SerializeException {
	}

}
