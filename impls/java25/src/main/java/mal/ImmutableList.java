/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collector;
import java.util.stream.Stream;

/**
 * <p>
 * Immutable segmented list (persistent-ish):
 * <ul>
 * <li>Backed by multiple fixed-size array segments (Object[])</li>
 * <li>Append and prepend create at most one new segment or clone a single end segment</li>
 * <li>Random access uses a binary search on prefix sums of segment sizes (O(log k))</li>
 * </ul>
 *
 * <p>
 * This is a simple, safe, and practical draft implementation. Segment size
 * should be chosen (e.g. 32, 64, 128) depending on workload.
 */
final class ImmutableList<E> implements Collection<E> {

  private static final int DEFAULT_SEGMENT_SIZE = 64;

  private final Object[][] segments; // each segment is tightly packed (0..count-1)
  private final int[] cumulativeSizes; // cumulativeSizes[i] = total elements up to and including segment i
  private final int totalSize;
  private final int segmentCapacity;

  private ImmutableList(Object[][] segments, int[] cumulativeSizes, int totalSize, int segmentCapacity) {
    this.segments = Objects.requireNonNull(segments);
    this.cumulativeSizes = Objects.requireNonNull(cumulativeSizes);
    this.totalSize = totalSize;
    this.segmentCapacity = segmentCapacity;
  }

  static <E> Collector<E, ?, ImmutableList<E>> toImmutableList() {
    return toImmutableList(DEFAULT_SEGMENT_SIZE);
  }

  static <E> Collector<E, ?, ImmutableList<E>> toImmutableList(int segmentCapacity) {
    return Collector.of(
        () -> new Builder<E>(segmentCapacity),
        Builder::append,
        Builder::merge,
        Builder::build);
  }

  static <E> Builder<E> builder() {
    return builder(DEFAULT_SEGMENT_SIZE);
  }

  static <E> Builder<E> builder(int segmentCapacity) {
    return new Builder<>(segmentCapacity);
  }

  @SafeVarargs
  static <E> ImmutableList<E> of(E... elems) {
    return from(Stream.of(elems));
  }

  public static <T> ImmutableList<T> empty() {
    return empty(DEFAULT_SEGMENT_SIZE);
  }

  public static <T> ImmutableList<T> empty(int segmentCapacity) {
    if (segmentCapacity <= 0) {
      throw new IllegalArgumentException("segmentCapacity must be > 0");
    }
    return new ImmutableList<>(new Object[0][], new int[0], 0, segmentCapacity);
  }

  public static <T> ImmutableList<T> from(Stream<? extends T> stream) {
    return from(stream, DEFAULT_SEGMENT_SIZE);
  }

  @SuppressWarnings("unchecked")
  public static <T> ImmutableList<T> from(Stream<? extends T> stream, int segmentCapacity) {
    Objects.requireNonNull(stream);
    return (ImmutableList<T>) from(stream::iterator, segmentCapacity);
  }

  public static <T> ImmutableList<T> from(Iterable<? extends T> elems) {
    return from(elems, DEFAULT_SEGMENT_SIZE);
  }

  @SuppressWarnings("unchecked")
  public static <T> ImmutableList<T> from(Iterable<? extends T> elems, int segmentCapacity) {
    Objects.requireNonNull(elems);
    if (elems instanceof ImmutableList<?>) {
      return (ImmutableList<T>) elems;
    }
    return new Builder<T>(segmentCapacity).appendAll(elems).build();
  }

  public Builder<E> toBuilder() {
    Builder<E> builder = new Builder<>(segmentCapacity);
    builder.merge(this);
    return builder;
  }

  @Override
  public int size() {
    return totalSize;
  }

  @Override
  public boolean isEmpty() {
    return totalSize == 0;
  }

  public int segmentCapacity() {
    return segmentCapacity;
  }

  @Override
  public boolean contains(Object o) {
    for (int i = 0; i < totalSize; ++i) {
      if (Objects.equals(get(i), o)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    for (Object o : c) {
      if (!contains(o)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Object[] toArray() {
    Object[] result = new Object[totalSize];
    for (int i = 0; i < totalSize; ++i) {
      result[i] = get(i);
    }
    return result;
  }

  @Override
  public <T> T[] toArray(T[] a) {
    throw new UnsupportedOperationException("toArray(T[] a) not implemented");
  }

  @SuppressWarnings("unchecked")
  public E get(int index) {
    if (index < 0 || index >= totalSize) {
      throw new IndexOutOfBoundsException(index);
    }
    int segIdx = findSegmentIndex(index);
    int segStart = segIdx == 0 ? 0 : cumulativeSizes[segIdx - 1];
    int offset = index - segStart;
    return (E) segments[segIdx][offset];
  }

  public E getFirst() {
    if (isEmpty()) {
      throw new NoSuchElementException();
    }
    return get(0);
  }

  public E getLast() {
    if (isEmpty()) {
      throw new NoSuchElementException();
    }
    return get(totalSize - 1);
  }

  // --- Mutation-style (returning new instances) ---

  public ImmutableList<E> concat(ImmutableList<E> other) {
    Objects.requireNonNull(other);
    if (other.isEmpty()) {
      return this;
    }
    if (this.isEmpty()) {
      return other;
    }

    // combine segments and cumulative sizes
    Object[][] newSegs = new Object[this.segments.length + other.segments.length][];
    System.arraycopy(this.segments, 0, newSegs, 0, this.segments.length);
    System.arraycopy(other.segments, 0, newSegs, this.segments.length, other.segments.length);

    int[] newCum = new int[this.cumulativeSizes.length + other.cumulativeSizes.length];
    System.arraycopy(this.cumulativeSizes, 0, newCum, 0, this.cumulativeSizes.length);
    int offset = this.totalSize;
    for (int i = 0; i < other.cumulativeSizes.length; ++i) {
      newCum[this.cumulativeSizes.length + i] = other.cumulativeSizes[i] + offset;
    }

    return new ImmutableList<>(newSegs, newCum, this.totalSize + other.totalSize, segmentCapacity);
  }

  public ImmutableList<E> append(E elem) {
    Objects.requireNonNull(elem);
    if (isEmpty()) {
      Object[] seg = new Object[segmentCapacity];
      seg[0] = elem;
      Object[][] newSegs = new Object[][] { seg };
      int[] newCum = new int[] { 1 };
      return new ImmutableList<>(newSegs, newCum, 1, segmentCapacity);
    }

    int lastIdx = segments.length - 1;
    int lastSegCount = cumulativeSizes[lastIdx] - (lastIdx == 0 ? 0 : cumulativeSizes[lastIdx - 1]);

    if (lastSegCount < segmentCapacity) {
      // clone last segment and add element
      Object[] newLast = segments[lastIdx].clone();
      newLast[lastSegCount] = elem;

      Object[][] newSegs = segments.clone();
      newSegs[lastIdx] = newLast;

      int[] newCum = cumulativeSizes.clone();
      newCum[lastIdx] = newCum[lastIdx] + 1;

      return new ImmutableList<>(newSegs, newCum, totalSize + 1, segmentCapacity);
    }

    // need to create a new segment at the end
    Object[] newSeg = new Object[segmentCapacity];
    newSeg[0] = elem;

    Object[][] newSegs = Arrays.copyOf(segments, segments.length + 1);
    newSegs[segments.length] = newSeg;

    int[] newCum = Arrays.copyOf(cumulativeSizes, cumulativeSizes.length + 1);
    newCum[newCum.length - 1] = totalSize + 1;

    return new ImmutableList<>(newSegs, newCum, totalSize + 1, segmentCapacity);
  }

  public ImmutableList<E> prepend(E elem) {
    Objects.requireNonNull(elem);
    if (isEmpty()) {
      Object[] seg = new Object[segmentCapacity];
      seg[0] = elem;
      Object[][] newSegs = new Object[][] { seg };
      int[] newCum = new int[] { 1 };
      return new ImmutableList<>(newSegs, newCum, 1, segmentCapacity);
    }

    // examine first segment
    int firstSegCount = cumulativeSizes[0];
    if (firstSegCount < segmentCapacity) {
      // clone first and shift right by 1 within the segment
      Object[] newFirst = new Object[segmentCapacity];
      System.arraycopy(segments[0], 0, newFirst, 1, firstSegCount);
      newFirst[0] = elem;

      Object[][] newSegs = segments.clone();
      newSegs[0] = newFirst;

      int[] newCum = cumulativeSizes.clone();
      for (int i = 0; i < newCum.length; ++i) {
        newCum[i] = newCum[i] + 1;
      }

      return new ImmutableList<>(newSegs, newCum, totalSize + 1, segmentCapacity);
    }

    // need a new segment in front
    Object[] newSeg = new Object[segmentCapacity];
    newSeg[0] = elem;

    Object[][] newSegs = new Object[segments.length + 1][];
    System.arraycopy(segments, 0, newSegs, 1, segments.length);
    newSegs[0] = newSeg;

    int[] newCum = new int[cumulativeSizes.length + 1];
    newCum[0] = 1;
    for (int i = 0; i < cumulativeSizes.length; ++i) {
      newCum[i + 1] = cumulativeSizes[i] + 1;
    }

    return new ImmutableList<>(newSegs, newCum, totalSize + 1, segmentCapacity);
  }

  public ImmutableList<E> dropFirst() {
    if (isEmpty()) {
      throw new NoSuchElementException("empty");
    }
    // remove element at index 0
    if (totalSize == 1) {
      return empty(segmentCapacity);
    }

    // operate on first segment
    int firstCount = cumulativeSizes[0];
    if (firstCount > 1) {
      // clone first and shift left by 1
      Object[] newFirst = new Object[segmentCapacity];
      System.arraycopy(segments[0], 1, newFirst, 0, firstCount - 1);

      Object[][] newSegs = segments.clone();
      newSegs[0] = newFirst;

      int[] newCum = cumulativeSizes.clone();
      for (int i = 0; i < newCum.length; ++i) {
        newCum[i] = newCum[i] - 1;
      }

      return new ImmutableList<>(newSegs, newCum, totalSize - 1, segmentCapacity);
    }

    // first segment had exactly 1 element -> drop it
    Object[][] newSegs = Arrays.copyOfRange(segments, 1, segments.length);
    int[] newCum = new int[cumulativeSizes.length - 1];
    for (int i = 0; i < newCum.length; ++i) {
      newCum[i] = cumulativeSizes[i + 1] - 1;
    }
    return new ImmutableList<>(newSegs, newCum, totalSize - 1, segmentCapacity);
  }

  public ImmutableList<E> dropLast() {
    if (isEmpty()) {
      throw new NoSuchElementException("empty");
    }
    if (totalSize == 1) {
      return empty(segmentCapacity);
    }

    int lastIdx = segments.length - 1;
    int lastCount = cumulativeSizes[lastIdx] - (lastIdx == 0 ? 0 : cumulativeSizes[lastIdx - 1]);

    if (lastCount > 1) {
      Object[] newLast = new Object[segmentCapacity];
      System.arraycopy(segments[lastIdx], 0, newLast, 0, lastCount - 1);

      Object[][] newSegs = segments.clone();
      newSegs[lastIdx] = newLast;

      int[] newCum = cumulativeSizes.clone();
      newCum[lastIdx] = newCum[lastIdx] - 1;

      return new ImmutableList<>(newSegs, newCum, totalSize - 1, segmentCapacity);
    }

    // drop last segment
    Object[][] newSegs = Arrays.copyOf(segments, segments.length - 1);
    int[] newCum = Arrays.copyOf(cumulativeSizes, cumulativeSizes.length - 1);
    return new ImmutableList<>(newSegs, newCum, totalSize - 1, segmentCapacity);
  }

  @Override
  public Iterator<E> iterator() {
    return new Iterator<>() {
      private int currentIndex = 0;

      @Override
      public boolean hasNext() {
        return currentIndex < totalSize;
      }

      @Override
      public E next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        return get(currentIndex++);
      }
    };
  }

  @Override
  public int hashCode() {
    int hash = 1;
    hash = 31 * hash + Arrays.hashCode(segments);
    hash = 31 * hash + Arrays.hashCode(cumulativeSizes);
    hash = 31 * hash + Integer.hashCode(segmentCapacity);
    hash = 31 * hash + Integer.hashCode(totalSize);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ImmutableList<?> other)) {
      return false;
    }
    if (this.size() != other.size()) {
      return false;
    }
    Iterator<E> it1 = this.iterator();
    Iterator<?> it2 = other.iterator();
    while (it1.hasNext() && it2.hasNext()) {
      E e1 = it1.next();
      Object e2 = it2.next();
      if (!Objects.equals(e1, e2)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("ImmutableList[size=").append(totalSize).append(", segCap=").append(segmentCapacity)
        .append("]{");
    boolean first = true;
    for (Object[] seg : segments) {
      int segCount = 0; // find count via cumulativeSizes mapping
      // find corresponding index
      int i = indexOfSegment(seg);
      if (i >= 0) {
        segCount = cumulativeSizes[i] - (i == 0 ? 0 : cumulativeSizes[i - 1]);
      }
      for (int j = 0; j < segCount; ++j) {
        if (!first) {
          sb.append(", ");
        }
        sb.append(seg[j]);
        first = false;
      }
    }
    sb.append('}');
    return sb.toString();
  }

  // mutable Collection methods throw UnsupportedOperationException

  @Override
  public boolean add(E e) {
    throw new UnsupportedOperationException("ImmutableList is immutable");
  }

  @Override
  public boolean addAll(Collection<? extends E> c) {
    throw new UnsupportedOperationException("ImmutableList is immutable");
  }

  @Override
  public boolean remove(Object o) {
    throw new UnsupportedOperationException("ImmutableList is immutable");
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    throw new UnsupportedOperationException("ImmutableList is immutable");
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    throw new UnsupportedOperationException("ImmutableList is immutable");
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException("ImmutableList is immutable");
  }

  // find segment index that contains logical index (0..totalSize-1)
  private int findSegmentIndex(int index) {
    // binary search on cumulativeSizes
    int lo = 0, hi = cumulativeSizes.length - 1;
    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      int cum = cumulativeSizes[mid];
      if (index < cum) {
        if (mid == 0 || index >= cumulativeSizes[mid - 1]) {
          return mid;
        }
        hi = mid - 1;
      } else {
        lo = mid + 1;
      }
    }
    throw new IllegalStateException("index not found: " + index);
  }

  private int indexOfSegment(Object[] needle) {
    for (int i = 0; i < segments.length; ++i) {
      if (segments[i] == needle) {
        return i;
      }
    }
    return -1;
  }

  static final class Builder<E> {
    final int segmentCapacity;
    Object[][] segments;
    int segmentCount;
    int[] segmentSizes;
    int totalSize;

    Builder(int segmentCapacity) {
      this.segmentCapacity = segmentCapacity;
      this.segments = new Object[4][];
      this.segmentSizes = new int[4];
    }

    Builder<E> prepend(E elem) {
      if (segmentCount == 0 || segmentSizes[0] == segmentCapacity) {
        // new segment at front
        if (segmentCount == segments.length) {
          segments = Arrays.copyOf(segments, segments.length * 2);
          segmentSizes = Arrays.copyOf(segmentSizes, segmentSizes.length * 2);
        }
        // shift existing segments right
        System.arraycopy(segments, 0, segments, 1, segmentCount);
        System.arraycopy(segmentSizes, 0, segmentSizes, 1, segmentCount);
        segments[0] = new Object[segmentCapacity];
        segmentSizes[0] = 0;
        segmentCount++;
      }
      // shift elements right within first segment
      System.arraycopy(segments[0], 0, segments[0], 1, segmentSizes[0]);
      segments[0][0] = elem;
      segmentSizes[0]++;
      totalSize++;
      return this;
    }

    Builder<E> prependAll(Iterable<? extends E> elems) {
      for (E e : elems) {
        prepend(e);
      }
      return this;
    }

    Builder<E> append(E elem) {
      if (segmentCount == 0 || segmentSizes[segmentCount - 1] == segmentCapacity) {
        // new segment
        if (segmentCount == segments.length) {
          segments = Arrays.copyOf(segments, segments.length * 2);
          segmentSizes = Arrays.copyOf(segmentSizes, segmentSizes.length * 2);
        }
        segments[segmentCount] = new Object[segmentCapacity];
        segmentSizes[segmentCount] = 0;
        segmentCount++;
      }
      segments[segmentCount - 1][segmentSizes[segmentCount - 1]++] = elem;
      totalSize++;
      return this;
    }

    Builder<E> appendAll(Iterable<? extends E> elems) {
      for (E e : elems) {
        append(e);
      }
      return this;
    }

    Builder<E> merge(Builder<E> other) {
      for (int i = 0; i < other.segmentCount; i++) {
        addSegment(other.segments[i], other.segmentSizes[i]);
      }
      return this;
    }

    Builder<E> merge(ImmutableList<E> other) {
      for (int i = 0; i < other.segments.length; i++) {
        int segSize = other.cumulativeSizes[i] - (i == 0 ? 0 : other.cumulativeSizes[i - 1]);
        addSegment(other.segments[i], segSize);
      }
      return this;
    }

    private void addSegment(Object[] seg, int size) {
      // copies only references, not elements
      if (segmentCount == segments.length) {
        segments = Arrays.copyOf(segments, segments.length * 2);
        segmentSizes = Arrays.copyOf(segmentSizes, segmentSizes.length * 2);
      }
      segments[segmentCount] = Arrays.copyOf(seg, segmentCapacity);
      segmentSizes[segmentCount] = size;
      segmentCount++;
      totalSize += size;
    }

    ImmutableList<E> build() {
      Object[][] finalSegs = Arrays.copyOf(segments, segmentCount);
      int[] cumulative = new int[segmentCount];
      int sum = 0;
      for (int i = 0; i < segmentCount; i++) {
        sum += segmentSizes[i];
        cumulative[i] = sum;
      }
      return new ImmutableList<>(finalSegs, cumulative, totalSize, segmentCapacity);
    }
  }
}
