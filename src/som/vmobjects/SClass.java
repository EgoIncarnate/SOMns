/**
 * Copyright (c) 2009 Michael Haupt, michael.haupt@hpi.uni-potsdam.de
 * Software Architecture Group, Hasso Plattner Institute, Potsdam, Germany
 * http://www.hpi.uni-potsdam.de/swa/
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package som.vmobjects;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import som.VM;
import som.compiler.AccessModifier;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.compiler.MixinDefinition;
import som.compiler.MixinDefinition.ClassSlotDefinition;
import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.objectstorage.ObjectLayout;
import som.vm.constants.Classes;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class SClass extends SObjectWithoutFields {

  @CompilationFinal private SClass superclass;
  @CompilationFinal private SSymbol name;

  @CompilationFinal private HashMap<SSymbol, Dispatchable> dispatchables;
  @CompilationFinal private HashSet<SlotDefinition> slots; // includes slots of super classes and mixins

  @CompilationFinal private ObjectLayout layoutForInstances;

  @CompilationFinal private MixinDefinition mixinDef;
  @CompilationFinal private boolean hasFields;
  @CompilationFinal private boolean hasOnlyImmutableFields;
  @CompilationFinal private boolean declaredAsValue;

  protected final SObjectWithoutFields enclosingObject;

  public SClass(final SObjectWithoutFields enclosing) {
    this.enclosingObject = enclosing;
  }

  public SClass(final SObjectWithoutFields enclosing, final SClass clazz) {
    super(clazz);
    this.enclosingObject = enclosing;
  }

  public SObjectWithoutFields getEnclosingObject() {
    return enclosingObject;
  }

  public SClass getSuperClass() {
    return superclass;
  }

  public HashSet<SlotDefinition> getInstanceSlots() {
    return slots;
  }

  public void setSuperClass(final SClass value) {
    transferToInterpreterAndInvalidate("SClass.setSuperClass");
    superclass = value;
  }

  public SSymbol getName() {
    return name;
  }

  public boolean declaredAsValue() {
    return declaredAsValue;
  }

  @Override
  public boolean isValue() {
    return enclosingObject.isValue();
  }

  public void setDeclaredAsValue(final boolean declaredAsValue) {
    this.declaredAsValue = declaredAsValue;
  }

  public MixinDefinition getMixinDefinition() {
    return mixinDef;
  }

  public void setMixinDefinition(final MixinDefinition mixinDef) {
    this.mixinDef = mixinDef;
  }

  private boolean isBasedOn(final MixinDefinitionId mixinId) {
    return this.mixinDef.getMixinId() == mixinId;
  }

  public boolean isKindOf(final SClass clazz) {
    if (this == clazz) { return true; }
    if (this == Classes.topClass) { return false; }
    return superclass.isKindOf(clazz);
  }

  public SClass getClassCorrespondingTo(final MixinDefinitionId mixinId) {
    VM.needsToBeOptimized("This should not be on the fast path, specialization/caching needed?");
    SClass cls = this;
    while (cls != null && !cls.isBasedOn(mixinId)) {
      cls = cls.getSuperClass();
    }
    return cls;
  }

  public void setName(final SSymbol value) {
    transferToInterpreterAndInvalidate("SClass.setName");
    assert name == null || name == value; // should not reset it, let's get the initialization right instead
    name = value;
  }

  public void setSlots(final HashSet<SlotDefinition> slots) {
    transferToInterpreterAndInvalidate("SClass.setInstanceFields");
    if (layoutForInstances == null) {
      layoutForInstances = new ObjectLayout(slots, this);
      this.slots = slots;
      this.hasFields = layoutForInstances.getNumberOfFields() > 0;
      this.hasOnlyImmutableFields = layoutForInstances.hasOnlyImmutableFields();
    } else {
      assert slots.size() == layoutForInstances.getNumberOfFields();
      assert slots.equals(this.slots);
    }
  }

  public boolean canUnderstand(final SSymbol selector) {
    return dispatchables.containsKey(selector);
  }

  public SInvokable[] getMethods() {
    ArrayList<SInvokable> methods = new ArrayList<SInvokable>();
    for (Dispatchable disp : dispatchables.values()) {
      if (disp instanceof SInvokable) {
        methods.add((SInvokable) disp);
      }
    }
    return methods.toArray(new SInvokable[methods.size()]);
  }

  public void setDispatchables(final HashMap<SSymbol, Dispatchable> value) {
    transferToInterpreterAndInvalidate("SClass.setDispatchables");
    dispatchables = value;
  }

  public SClass[] getNestedClasses(final SObjectWithoutFields instance) {
    CompilerAsserts.neverPartOfCompilation("Not optimized, we do unrecorded invokes here");
    ArrayList<SClass> classes = new ArrayList<SClass>();
    for (Dispatchable disp : dispatchables.values()) {
      if (disp instanceof ClassSlotDefinition) {
        classes.add((SClass) disp.invoke(instance));
      }
    }
    return classes.toArray(new SClass[classes.size()]);
  }

  @TruffleBoundary
  public Dispatchable lookupPrivate(final SSymbol selector,
      final MixinDefinitionId mixinId) {
    SClass cls = getClassCorrespondingTo(mixinId);
    if (cls != null) {
      Dispatchable disp = cls.dispatchables.get(selector);
      if (disp != null && disp.getAccessModifier() == AccessModifier.PRIVATE) {
        return disp;
      }
    }
    return lookupMessage(selector, AccessModifier.PROTECTED);
  }

  @TruffleBoundary
  public Dispatchable lookupMessage(final SSymbol selector,
      final AccessModifier hasAtLeast) {
    assert hasAtLeast.ordinal() >= AccessModifier.PROTECTED.ordinal();

    Dispatchable disp = dispatchables.get(selector);

    if (disp != null && disp.getAccessModifier().ordinal() >= hasAtLeast.ordinal()) {
      return disp;
    }

    if (superclass == Classes.topClass) {
      return null;
    } else {
      return superclass.lookupMessage(selector, hasAtLeast);
    }
  }

  public boolean hasFields() {
    if (layoutForInstances == null) { return false; }
    return hasFields;
  }

  public boolean hasOnlyImmutableFields() {
    return hasOnlyImmutableFields;
  }

  public ObjectLayout getLayoutForInstances() {
    return layoutForInstances;
  }

  public synchronized ObjectLayout updateInstanceLayoutWithInitializedField(
      final SlotDefinition slot, final Class<?> type) {
    ObjectLayout updated = layoutForInstances.withInitializedField(slot, type);

    if (updated != layoutForInstances) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      layoutForInstances = updated;
    }
    return layoutForInstances;
  }

  public synchronized ObjectLayout updateInstanceLayoutWithGeneralizedField(
      final SlotDefinition slot) {
    ObjectLayout updated = layoutForInstances.withGeneralizedField(slot);

    if (updated != layoutForInstances) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      layoutForInstances = updated;
    }
    return layoutForInstances;
  }

  @Override
  public String toString() {
    return "Class(" + getName().getString() + ")";
  }
}
