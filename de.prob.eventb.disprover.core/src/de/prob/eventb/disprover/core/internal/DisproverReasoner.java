package de.prob.eventb.disprover.core.internal;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.Status;
import org.eventb.core.IContextRoot;
import org.eventb.core.IEventBProject;
import org.eventb.core.IEventBRoot;
import org.eventb.core.IMachineRoot;
import org.eventb.core.ast.Predicate;
import org.eventb.core.basis.POSequent;
import org.eventb.core.seqprover.IConfidence;
import org.eventb.core.seqprover.IProofMonitor;
import org.eventb.core.seqprover.IProofRule;
import org.eventb.core.seqprover.IProofRule.IAntecedent;
import org.eventb.core.seqprover.IProverSequent;
import org.eventb.core.seqprover.IReasoner;
import org.eventb.core.seqprover.IReasonerInput;
import org.eventb.core.seqprover.IReasonerInputReader;
import org.eventb.core.seqprover.IReasonerInputWriter;
import org.eventb.core.seqprover.IReasonerOutput;
import org.eventb.core.seqprover.ProverFactory;
import org.eventb.core.seqprover.SerializeException;
import org.rodinp.core.RodinDBException;
import org.rodinp.core.basis.InternalElement;

import de.prob.core.Animator;
import de.prob.core.PrologException;
import de.prob.eventb.disprover.core.DisproverReasonerInput;
import de.prob.eventb.disprover.core.ICounterExample;
import de.prob.exceptions.ProBException;
import de.prob.logging.Logger;

public class DisproverReasoner implements IReasoner {

	static final String DISPROVER_CONTEXT = "disprover_context";

	private static final String DISPROVER_REASONER_NAME = "de.prob.eventb.disprover.core.disproverReasoner";

	@Override
	public String getReasonerID() {
		return DISPROVER_REASONER_NAME;
	}

	/**
	 * Applies the Disprover by building a machine from Goal and Hypotheses.
	 */
	@Override
	public IReasonerOutput apply(final IProverSequent sequent,
			final IReasonerInput input, final IProofMonitor pm) {
		try {
			DisproverReasonerInput disproverInput = (DisproverReasonerInput) input;
			ICounterExample ce = evaluateSequent(sequent, disproverInput);
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
		}
	}

	private ICounterExample evaluateSequent(final IProverSequent sequent,
			final DisproverReasonerInput disproverInput) throws ProBException,
			RodinDBException {

		Set<Predicate> hypotheses = new HashSet<Predicate>();
		for (Predicate predicate : sequent.visibleHypIterable()) {
			hypotheses.add(predicate);
		}
		Predicate goal = sequent.goal();

		IEventBRoot root = getRoot(sequent);
		ICounterExample counterExample = DisproverCommand.disprove(
				Animator.getAnimator(), hypotheses, goal, root);
		return counterExample;
	}

	private IEventBRoot getRoot(IProverSequent sequent) {
		POSequent origin = (POSequent) sequent.getOrigin();
		InternalElement poRoot = origin.getRoot();

		// IPORoot poRoot = origin.getComponent().getPORoot();
		String name = poRoot.getElementName();
		IEventBProject eventBProject = (IEventBProject) poRoot
				.getRodinProject().getAdapter(IEventBProject.class);

		// We don't know whether we have a machine or a context.
		IMachineRoot machineRoot = eventBProject.getMachineRoot(name);
		IContextRoot contextRoot = eventBProject.getContextRoot(name);

		if (machineRoot.exists()) {
			return machineRoot;

		} else if (contextRoot.exists()) {
			return contextRoot;
		} else {
			// Neither Machine nor Context
			throw new RuntimeException(
					"Cannot use ProB Disprover on non Machine/Context Files");
		}
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
					"Timeout occurred (ProB)");

		if (!counterExample.counterExampleFound() && counterExample.isProof())
			return ProverFactory.makeProofRule(this, input, sequent.goal(),
					null, IConfidence.DISCHARGED_MAX,
					"ProB (all cases checked)");

		if (!counterExample.counterExampleFound())
			return ProverFactory
					.reasonerFailure(this, input,
							"No Counter-Example found (ProB), but there might exist one");

		return ProverFactory.makeProofRule(this, input, null, null,
				IConfidence.PENDING,
				"Counter-Example: " + counterExample.toString(), ante);
	}

	@Override
	public IReasonerInput deserializeInput(final IReasonerInputReader reader)
			throws SerializeException {
		return null;
	}

	@Override
	public void serializeInput(final IReasonerInput input,
			final IReasonerInputWriter writer) throws SerializeException {
	}

}
