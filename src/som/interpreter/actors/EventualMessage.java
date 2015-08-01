package som.interpreter.actors;

import java.util.Arrays;
import java.util.concurrent.RecursiveAction;

import som.compiler.AccessModifier;
import som.interpreter.Types;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;


public final class EventualMessage extends RecursiveAction {
  private static final long serialVersionUID = -7994739264831630827L;

  private Actor target;
  private final SSymbol  selector;
  private final Object[] args;
  private final SResolver resolver;

  private Actor sender;

  public EventualMessage(final Actor target, final SSymbol selector,
      final Object[] args, final SResolver resolver, final Actor sender) {
    this.target   = target;
    this.selector = selector;
    this.args     = args;
    this.resolver = resolver;
    assert resolver != null;

    this.sender = sender;
  }

  public void setReceiverForEventualPromiseSend(final Object rcvr, final Actor target, final Actor sendingActor) {
    this.target = target;
    args[0] = target.wrapForUse(rcvr, sendingActor);

    for (int i = 1; i < args.length; i++) {
      args[i] = target.wrapForUse(args[i], sendingActor);
    }
  }

  public Actor getTarget() {
    return target;
  }

  public boolean isReceiverSet() {
    return args[0] != null;
  }

  @Override
  protected void compute() {
    actorThreadLocal.set(target);

    try {
      executeMessage();
    } catch (Throwable t) {
      t.printStackTrace();
    }

    actorThreadLocal.set(null);
    target.enqueueNextMessageForProcessing();
  }

  protected void executeMessage() {
    CompilerAsserts.neverPartOfCompilation("Not Optimized! But also not sure it can be part of compilation anyway");

    assert sender != null;

    Object rcvrObj = args[0];
    assert rcvrObj != null;

    Object result;
    assert !(rcvrObj instanceof SFarReference);

    Dispatchable disp = Types.getClassOf(rcvrObj).
        lookupMessage(selector, AccessModifier.PUBLIC);
    result = disp.invoke(args);

    resolver.resolve(result);
  }

  public static Actor getActorCurrentMessageIsExecutionOn() {
    return actorThreadLocal.get();
  }

  public static void setMainActor(final Actor actor) {
    actorThreadLocal.set(actor);
  }

  @Override
  public String toString() {
    String t;
    if (target == null) {
      t = "null";
    } else {
      t = target.toString();
    }
    return "EMsg(" + selector.toString() + ", " + Arrays.toString(args) +  ", " + t + ", sender: " + sender.toString() + ")";
  }

  private static final ThreadLocal<Actor> actorThreadLocal = new ThreadLocal<Actor>();
}
