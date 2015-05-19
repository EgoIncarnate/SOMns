package som.vm.constants;

import som.vm.Bootstrap;
import som.vmobjects.SClass;


public final class Classes {
  public static final SClass  topClass;
  public static final SClass  thingClass;
  public static final SClass  objectClass;
  public static final SClass  valueClass;
  public static final SClass  classClass;
  public static final SClass  metaclassClass;

  public static final SClass  nilClass;
  public static final SClass  integerClass;
  public static final SClass  arrayClass;
  public static final SClass  methodClass;
  public static final SClass  symbolClass;
  public static final SClass  primitiveClass;
  public static final SClass  stringClass;
  public static final SClass  doubleClass;

  public static final SClass  booleanClass;

  // These classes can be statically preinitialized.
  static {
    // Allocate the Metaclass classes
    metaclassClass = Bootstrap.newMetaclassClass();

    // Allocate the rest of the system classes
    topClass        = Bootstrap.newEmptyClassWithItsClass("Top");
    thingClass      = Bootstrap.newEmptyClassWithItsClass("Thing");
    objectClass     = Bootstrap.newEmptyClassWithItsClass("Object");
    valueClass      = Bootstrap.newEmptyClassWithItsClass("Value");
    nilClass        = Bootstrap.newEmptyClassWithItsClass("Nil");
    classClass      = Bootstrap.newEmptyClassWithItsClass("Class");
    arrayClass      = Bootstrap.newEmptyClassWithItsClass("Array");
    symbolClass     = Bootstrap.newEmptyClassWithItsClass("Symbol");
    methodClass     = Bootstrap.newEmptyClassWithItsClass("Method");
    integerClass    = Bootstrap.newEmptyClassWithItsClass("Integer");
    primitiveClass  = Bootstrap.newEmptyClassWithItsClass("Primitive");
    stringClass     = Bootstrap.newEmptyClassWithItsClass("String");
    doubleClass     = Bootstrap.newEmptyClassWithItsClass("Double");
    booleanClass    = Bootstrap.newEmptyClassWithItsClass("Boolean");
  }
}
