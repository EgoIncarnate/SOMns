package dym.profiles;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import som.interpreter.Invokable;

import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.JSONHelper;
import com.oracle.truffle.api.utilities.JSONHelper.JSONArrayBuilder;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;

public class InvocationProfile extends Counter {

  private final Invokable method;
  private final Map<Arguments, Integer> argumentTypes;

  public InvocationProfile(final SourceSection source, final Invokable method) {
    super(source);
    argumentTypes = new HashMap<>();
    this.method   = method;
  }

  public Invokable getMethod() {
    return method;
  }

  public void profileArguments(final Object[] args) {
    argumentTypes.merge(
        new Arguments(args), 1, Integer::sum);
  }

  @Override
  public JSONArrayBuilder toJson() {
    JSONArrayBuilder result = JSONHelper.array();
    for (Entry<Arguments, Integer> e : argumentTypes.entrySet()) {
      JSONObjectBuilder invocations = e.getKey().toJson();
      invocations.add("invocations", e.getValue());
      result.add(invocations);
    }
    return result;
  }

  @Override
  public String toString() {
    return "InvProf" + argumentsMapToString(argumentTypes);
  }

  public static String argumentsMapToString(final Map<Arguments, Integer> args) {
    String result = "[";
    for (Entry<Arguments, Integer> e : args.entrySet()) {
      if (!result.equals("[")) {
        result += "; ";
      }
      result += e.getKey().toString() + "=" + e.getValue();
    }
    return result + "]";
  }
}
