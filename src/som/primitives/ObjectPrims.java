package som.primitives;

import java.math.BigInteger;

import som.VM;
import som.interpreter.Types;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.constants.Nil;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;


public final class ObjectPrims {

  @GenerateNodeFactory
  @Primitive("objClassName:")
  public abstract static class ObjectClassNamePrim extends UnaryExpressionNode {
    @Specialization
    public final SSymbol getName(final Object obj) {
      VM.needsToBeOptimized("Not yet optimized, need add specializations to remove Types.getClassOf");
      return Types.getClassOf(obj).getName();
    }
  }

  @GenerateNodeFactory
  @Primitive("halt:")
  public abstract static class HaltPrim extends UnaryExpressionNode {
    public HaltPrim() { super(null); }
    @Specialization
    public final Object doSAbstractObject(final Object receiver) {
      VM.errorPrintln("BREAKPOINT");
      return receiver;
    }
  }

  @GenerateNodeFactory
  @Primitive("objClass:")
  public abstract static class ClassPrim extends UnaryExpressionNode {
    @Specialization
    public final SClass doSAbstractObject(final SAbstractObject receiver) {
      return receiver.getSOMClass();
    }

    @Specialization
    public final SClass doObject(final Object receiver) {
      VM.needsToBeOptimized("Should specialize this if performance critical");
      return Types.getClassOf(receiver);
    }
  }

  public abstract static class IsNilNode extends UnaryExpressionNode {
    @Specialization
    public final boolean isNil(final Object receiver) {
      return receiver == Nil.nilObject;
    }
  }

  public abstract static class NotNilNode extends UnaryExpressionNode {
    @Specialization
    public final boolean isNotNil(final Object receiver) {
      return receiver != Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @Primitive("objIsValue:")
  @ImportStatic(Nil.class)
  public abstract static class IsValue extends UnaryExpressionNode {
    @Specialization
    public final boolean isValue(final boolean rcvr) {
      return true;
    }

    @Specialization
    public final boolean isValue(final long rcvr) {
      return true;
    }

    @Specialization
    public final boolean isValue(final BigInteger rcvr) {
      return true;
    }

    @Specialization
    public final boolean isValue(final double rcvr) {
      return true;
    }

    @Specialization
    public final boolean isValue(final String rcvr) {
      return true;
    }

    @Specialization
    public final boolean isValue(final SSymbol rcvr) {
      return true;
    }

    @Specialization
    public final boolean isValue(final SArray rcvr) {
      return false;
    }

    @Specialization(guards = "valueIsNil(rcvr)")
    public final boolean nilIsValue(final Object rcvr) {
      return true;
    }

    @Specialization
    public final boolean isValue(final SAbstractObject obj) {
      return obj.isValue();
    }

    public static boolean isObjectValue(final Object obj) {
      VM.needsToBeOptimized("This should only be used for prototyping, and then removed, because it is slow and duplicates code");
      if (obj instanceof Boolean ||
          obj instanceof Long ||
          obj instanceof BigInteger ||
          obj instanceof Double ||
          obj instanceof String) {
        return true;
      }

      if (Nil.valueIsNil(obj)) {
        return true;
      }

      return ((SAbstractObject) obj).isValue();
    }
  }
}
