package som.primitives;

import java.math.BigInteger;

import som.interpreter.nodes.nary.UnaryBasicOperation;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;


@GenerateNodeFactory
@Primitive({"symbolAsString:", "intAsString:", "doubleAsString:"})
public abstract class AsStringPrim extends UnaryBasicOperation {
  public AsStringPrim(final SourceSection source) { super(source); }

  @Specialization
  public final String doSSymbol(final SSymbol receiver) {
    return receiver.getString();
  }

  @TruffleBoundary
  @Specialization
  public final String doLong(final long receiver) {
    return Long.toString(receiver);
  }

  @TruffleBoundary
  @Specialization
  public final String doDouble(final double receiver) {
    return Double.toString(receiver);
  }

  @TruffleBoundary
  @Specialization
  public final String doBigInteger(final BigInteger receiver) {
    return receiver.toString();
  }
}
