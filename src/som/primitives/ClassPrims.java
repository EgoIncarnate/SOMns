package som.primitives;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;


public class ClassPrims {

  // TODO: move to new mirror class
  @GenerateNodeFactory
  @Primitive("mirrorAClassesName:")
  public abstract static class NamePrim extends UnaryExpressionNode {
    @Specialization
    public final SAbstractObject doSClass(final SClass receiver) {
      return receiver.getName();
    }
  }

  @GenerateNodeFactory
  public abstract static class SuperClassPrim extends UnaryExpressionNode {
    @Specialization
    public final SAbstractObject doSClass(final SClass receiver) {
      return receiver.getSuperClass();
    }
  }

}
