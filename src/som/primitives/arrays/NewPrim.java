package som.primitives.arrays;

import java.util.Arrays;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.primitives.Primitive;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SClass;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;


@GenerateNodeFactory
@Primitive("array:new:")
public abstract class NewPrim extends BinaryExpressionNode {

  protected final AllocProfile allocProfile;

  public NewPrim() { allocProfile = new AllocProfile(); }
  public NewPrim(final NewPrim clone) { allocProfile = clone.allocProfile; }

  public static class AllocProfile {
    @CompilationFinal private boolean becomesObject;
    public boolean isBecomingObject() { return becomesObject; }
    public void doesBecomeObject() {
      if (!becomesObject) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        becomesObject = true;
      }
    }
  }

  public final boolean isBecomingObject(final SClass receiver) {
    return allocProfile.isBecomingObject();
  }

  public final boolean isNotBecomingObject(final SClass receiver) {
    return !allocProfile.isBecomingObject();
  }

  protected static final boolean receiverIsArrayClass(final SClass receiver) {
    return receiver == Classes.arrayClass;
  }

  @Specialization(guards = {"isNotBecomingObject(receiver)", "receiverIsArrayClass(receiver)"})
  public final SArray doSpecializingArray(final SClass receiver, final long length) {
    return new SArray(length, allocProfile);
  }

  @Specialization(guards = {"isBecomingObject(receiver)", "receiverIsArrayClass(receiver)"})
  public final SArray doObjectArray(final SClass receiver, final long length) {
    Object[] storage = new Object[(int) length];
    Arrays.fill(storage, Nil.nilObject);
    return new SArray(true, storage);
  }
}
