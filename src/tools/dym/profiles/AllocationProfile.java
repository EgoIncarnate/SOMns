package tools.dym.profiles;

import som.vmobjects.SClass;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;

import com.oracle.truffle.api.source.SourceSection;


public class AllocationProfile extends Counter {

  private int numFields;
  private SClass type;

  public AllocationProfile(final SourceSection source) {
    super(source);
    this.numFields = -1;
  }

  public void recordResult(final SObjectWithClass obj) {
    int objFields;
    if (obj.getSOMClass().getLayoutForInstances() == null) {
      assert obj instanceof SObjectWithoutFields : "If obj is something else, I need support for it.";
      objFields = 0;
    } else {
      objFields = obj.getSOMClass().getLayoutForInstances().getNumberOfFields();
    }

    if (numFields == -1) {
      numFields = objFields;
      type = obj.getSOMClass();
    } else if (numFields != objFields || type != obj.getSOMClass()) {
      throw new RuntimeException("This is unexpected. " +
          "Are ther multiple classes allocated here? Might be, inheritance? " +
          "TODO: Add support for polymorphic allocation sites.");
    }
  }

  public int getNumberOfObjectFields() {
    return numFields;
  }

  public SClass getSOMClass() {
    return type;
  }
}
