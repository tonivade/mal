/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;

import org.pcollections.AbstractUnmodifiableList;
import org.pcollections.PVector;
import org.pcollections.TreePVector;

// a persistent vector backed by an array, for the short lists that are most of the lists of a
// program, like the arguments of a call or the forms of the code; get and iteration are as fast
// as in an array, but every change copies it, so a vector that grows past MAX_SIZE becomes
// a TreePVector, where a change is O(log n)
final class ArrayPVector<E> extends AbstractUnmodifiableList<E> implements PVector<E> {

  static final int MAX_SIZE = 32;

  private static final ArrayPVector<?> EMPTY = new ArrayPVector<>(new Object[0]);

  private final Object[] values;

  private ArrayPVector(Object[] values) {
    this.values = values;
  }

  @SuppressWarnings("unchecked")
  static <E> ArrayPVector<E> empty() {
    return (ArrayPVector<E>) EMPTY;
  }

  static <E> PVector<E> from(Collection<? extends E> values) {
    if (values.isEmpty()) {
      return empty();
    }
    return of(values.toArray());
  }

  // the array must not be shared, the vector owns it
  @SuppressWarnings("unchecked")
  private static <E> PVector<E> of(Object[] values) {
    if (values.length > MAX_SIZE) {
      return TreePVector.from(Arrays.asList((E[]) values));
    }
    return new ArrayPVector<>(values);
  }

  @Override
  public int size() {
    return values.length;
  }

  @Override
  @SuppressWarnings("unchecked")
  public E get(int index) {
    return (E) values[index];
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterator<E> iterator() {
    return Arrays.asList((E[]) values).iterator();
  }

  @Override
  public PVector<E> plus(E element) {
    return plus(values.length, element);
  }

  @Override
  public PVector<E> plus(int index, E element) {
    Objects.checkIndex(index, values.length + 1);
    var copy = new Object[values.length + 1];
    System.arraycopy(values, 0, copy, 0, index);
    copy[index] = element;
    System.arraycopy(values, index, copy, index + 1, values.length - index);
    return of(copy);
  }

  @Override
  public PVector<E> plusAll(Collection<? extends E> elements) {
    return plusAll(values.length, elements);
  }

  @Override
  public PVector<E> plusAll(int index, Collection<? extends E> elements) {
    Objects.checkIndex(index, values.length + 1);
    var added = elements.toArray();
    if (added.length == 0) {
      return this;
    }
    var copy = new Object[values.length + added.length];
    System.arraycopy(values, 0, copy, 0, index);
    System.arraycopy(added, 0, copy, index, added.length);
    System.arraycopy(values, index, copy, index + added.length, values.length - index);
    return of(copy);
  }

  @Override
  public PVector<E> with(int index, E element) {
    Objects.checkIndex(index, values.length);
    var copy = values.clone();
    copy[index] = element;
    return new ArrayPVector<>(copy);
  }

  @Override
  public PVector<E> minus(int index) {
    Objects.checkIndex(index, values.length);
    var copy = new Object[values.length - 1];
    System.arraycopy(values, 0, copy, 0, index);
    System.arraycopy(values, index + 1, copy, index, values.length - index - 1);
    return new ArrayPVector<>(copy);
  }

  @Override
  public PVector<E> minus(Object element) {
    var index = indexOf(element);
    if (index < 0) {
      return this;
    }
    return minus(index);
  }

  @Override
  public PVector<E> minusAll(Collection<?> elements) {
    // like TreePVector, only the first occurrence of each element is removed
    PVector<E> result = this;
    for (var element : elements) {
      result = result.minus(element);
    }
    return result;
  }

  @Override
  public PVector<E> subList(int fromIndex, int toIndex) {
    Objects.checkFromToIndex(fromIndex, toIndex, values.length);
    if (fromIndex == 0 && toIndex == values.length) {
      return this;
    }
    return new ArrayPVector<>(Arrays.copyOfRange(values, fromIndex, toIndex));
  }
}
