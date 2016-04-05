package som.primitives.actors;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.actors.Actor;
import som.interpreter.actors.SFarReference;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.primitives.ObjectPrims.IsValue;
import som.primitives.Primitive;


@GenerateNodeFactory
@Primitive("actors:createFromValue:")
@NodeChild(value = "isValue", type = IsValue.class, executeWith = "receiver")
public abstract class CreateActorPrim extends BinaryComplexOperation {
  protected CreateActorPrim(final SourceSection source) { super(false, source); }

  @Specialization(guards = "isValue")
  public final SFarReference createActor(final Object nil, final Object value, final boolean isValue) {
    Actor actor = Actor.createActor();
    SFarReference ref = new SFarReference(actor, value);
    return ref;
  }

  // TODO: add a proper error or something if it isn't a value...
}
