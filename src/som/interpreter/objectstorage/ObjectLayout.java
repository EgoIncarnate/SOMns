package som.interpreter.objectstorage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import som.compiler.ClassDefinition.SlotDefinition;
import som.interpreter.objectstorage.StorageLocation.UnwrittenStorageLocation;
import som.vmobjects.SClass;
import som.vmobjects.SObject;


public final class ObjectLayout {
  private final SClass forClass;

  private final int primitiveStorageLocationsUsed;
  private final int objectStorageLocationsUsed;
  private final int totalNumberOfStorageLocations;

  private final HashMap<SlotDefinition, StorageLocation> storageLocations;
  private final HashMap<SlotDefinition, Class<?>>        storageTypes;

  public ObjectLayout(final HashSet<SlotDefinition> slots, final SClass forClass) {
    this(getInitialStorageTypes(slots), slots.size(), forClass);
  }

  private static HashMap<SlotDefinition, Class<?>> getInitialStorageTypes(
      final HashSet<SlotDefinition> slots) {
    HashMap<SlotDefinition, Class<?>> types = new HashMap<SlotDefinition, Class<?>>((int) (slots.size() / 0.75f));
    for (SlotDefinition slot : slots) {
      types.put(slot, null);
    }
    return types;
  }

  public ObjectLayout(final HashMap<SlotDefinition, Class<?>> knownFieldTypes,
      final int numberOfFields, final SClass forClass) {
    this.forClass = forClass;

    storageTypes = knownFieldTypes;
    totalNumberOfStorageLocations = numberOfFields;
    storageLocations = new HashMap<>((int) (numberOfFields / 0.75f));

    int nextFreePrimIdx = 0;
    int nextFreeObjIdx  = 0;

    for (Entry<SlotDefinition, Class<?>> entry : knownFieldTypes.entrySet()) {
      StorageLocation storage;
      if (entry.getValue() == Long.class) {
        storage = StorageLocation.createForLong(this, nextFreePrimIdx);
        nextFreePrimIdx++;
      } else if (entry.getValue() == Double.class) {
        storage = StorageLocation.createForDouble(this, nextFreePrimIdx);
        nextFreePrimIdx++;
      } else if (entry.getValue() == Object.class) {
        storage = StorageLocation.createForObject(this, nextFreeObjIdx);
        nextFreeObjIdx++;
      } else {
        assert entry.getValue() == null;
        storage = new UnwrittenStorageLocation(this);
      }
      storageLocations.put(entry.getKey(), storage);
    }

    primitiveStorageLocationsUsed = nextFreePrimIdx;
    objectStorageLocationsUsed    = nextFreeObjIdx;
  }

  public boolean layoutForSameClass(final ObjectLayout other) {
    return forClass == other.forClass;
  }

  public int getNumberOfFields() {
    return totalNumberOfStorageLocations;
  }

  public HashMap<SlotDefinition, StorageLocation> getStorageLocations() {
    return storageLocations;
  }

  public ObjectLayout withGeneralizedField(final SlotDefinition slot) {
    Class<?> type = storageTypes.get(slot);
    if (type == Object.class) {
      return this;
    } else {
      assert type != Object.class;
      return cloneWithChanged(slot, Object.class);
    }
  }

  public ObjectLayout withInitializedField(final SlotDefinition slot, final Class<?> type) {
    Class <?> specType;
    if (type == Long.class || type == Double.class) {
      specType = type;
    } else {
      specType = Object.class;
    }

    Class<?> currentType = storageTypes.get(slot);
    if (currentType == specType) {
      return this;
    } else {
      assert currentType == null;
      return cloneWithChanged(slot, specType);
    }
  }

  protected ObjectLayout cloneWithChanged(final SlotDefinition slot,
      final Class<?> specType) {
    HashMap<SlotDefinition, Class<?>> withChangedField = new HashMap<>(storageTypes);
    withChangedField.put(slot, specType);
    return new ObjectLayout(withChangedField, totalNumberOfStorageLocations, forClass);
  }

  public StorageLocation getStorageLocation(final SlotDefinition slot) {
    return storageLocations.get(slot);
  }

  public int getNumberOfUsedExtendedObjectStorageLocations() {
    int requiredExtensionFields = objectStorageLocationsUsed - SObject.NUM_OBJECT_FIELDS;
    if (requiredExtensionFields < 0) { return 0; }
    return requiredExtensionFields;
  }

  public int getNumberOfUsedExtendedPrimStorageLocations() {
    int requiredExtensionFields = primitiveStorageLocationsUsed - SObject.NUM_PRIMITIVE_FIELDS;
    if (requiredExtensionFields < 0) { return 0;  }
    return requiredExtensionFields;
  }
}
