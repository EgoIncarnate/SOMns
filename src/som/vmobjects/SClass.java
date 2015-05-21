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

import java.util.HashMap;

import som.compiler.AccessModifier;
import som.compiler.ClassDefinition.SlotDefinition;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.objectstorage.ObjectLayout;
import som.vm.constants.Classes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class SClass extends SObjectWithoutFields {

  @CompilationFinal private SClass superclass;
  @CompilationFinal private SSymbol name;

  @CompilationFinal private HashMap<SSymbol, SInvokable>     instanceInvokables;
  @CompilationFinal private HashMap<SSymbol, SlotDefinition> instanceSlots;

  @CompilationFinal private ObjectLayout layoutForInstances;

  public SClass(final SAbstractObject enclosing) {
    super(enclosing);
  }

  public SClass(final SAbstractObject enclosing, final SClass clazz) {
    super(enclosing, clazz);
  }

  public SObjectWithoutFields getSuperClass() {
    return superclass;
  }

  public void setSuperClass(final SClass value) {
    transferToInterpreterAndInvalidate("SClass.setSuperClass");
    superclass = value;
  }

  public SSymbol getName() {
    return name;
  }

  public void setName(final SSymbol value) {
    transferToInterpreterAndInvalidate("SClass.setName");
    assert name == null || name == value; // should not reset it, let's get the initialization right instead
    name = value;
  }

  public void setInstanceSlots(final HashMap<SSymbol, SlotDefinition> slots) {
    transferToInterpreterAndInvalidate("SClass.setInstanceFields");
    instanceSlots = slots;
    if (layoutForInstances == null ||
        slots.size() != layoutForInstances.getNumberOfFields()) {
      layoutForInstances = new ObjectLayout(slots.size(), this);
    }
  }

  public void setInstanceInvokables(final HashMap<SSymbol, SInvokable> value) {
    transferToInterpreterAndInvalidate("SClass.setInstanceInvokables");
    instanceInvokables = value;
    for (SInvokable invokable : value.values()) {
      invokable.setHolder(this);
    }
  }

  @TruffleBoundary
  public Dispatchable lookupMessage(final SSymbol selector,
      final AccessModifier hasAtLeast) {
    Dispatchable disp = instanceInvokables.get(selector);
    if (disp == null) { disp = instanceSlots.get(selector); }

    if (disp != null && disp.getAccessModifier().ordinal() >= hasAtLeast.ordinal()) {
      return disp;
    }

    if (superclass == Classes.topClass) {
      return null;
    } else {
      AccessModifier atLeastProtected;
      if (hasAtLeast.ordinal() < AccessModifier.PROTECTED.ordinal()) {
        atLeastProtected = AccessModifier.PROTECTED;
      } else {
        atLeastProtected = hasAtLeast;
      }
      return superclass.lookupMessage(selector, atLeastProtected);
    }
  }

  public int getNumberOfInstanceFields() {
    return instanceSlots.size();
  }

  public ObjectLayout getLayoutForInstances() {
    return layoutForInstances;
  }

  public ObjectLayout updateInstanceLayoutWithInitializedField(final long index, final Class<?> type) {
    ObjectLayout updated = layoutForInstances.withInitializedField(index, type);

    if (updated != layoutForInstances) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      layoutForInstances = updated;
    }
    return layoutForInstances;
  }

  public ObjectLayout updateInstanceLayoutWithGeneralizedField(final long index) {
    ObjectLayout updated = layoutForInstances.withGeneralizedField(index);

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
