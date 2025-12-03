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

  // --- Constructors ---
  private ImmutableList(Object[][] segments, int[] cumulativeSizes, int totalSize, int segmentCapacity) {
    this.segments = Objects.requireNonNull(segments);
    this.cumulativeSizes = Objects.requireNonNull(cumulativeSizes);
    this.totalSize = totalSize;
    this.segmentCapacity = segmentCapacity;
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

  @SuppressWarnings("unchecked")
  public static <T> ImmutableList<T> of(Collection<? extends T> elems) {
    Objects.requireNonNull(elems);
    if (elems instanceof ImmutableList<?>) {
      return (ImmutableList<T>) elems;
    }
    ImmutableList<T> list = empty();
    for (T e : elems) {
      list = list.append(e);
    }
    return list;
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
      throw new IndexOutOfBoundsException("index=" + index);
    }
    int segIdx = findSegmentIndex(index);
    int segStart = segIdx == 0 ? 0 : cumulativeSizes[segIdx - 1];
    int offset = index - segStart;
    return (E) segments[segIdx][offset];
  }

  // --- Mutation-style (returning new instances) ---

  public ImmutableList<E> concat(ImmutableList<E> other) {
    Objects.requireNonNull(other);
    if (other.totalSize == 0) {
      return this;
    }
    if (this.totalSize == 0) {
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
    if (totalSize == 0) {
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
    if (totalSize == 0) {
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
    if (totalSize == 0) {
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
    if (totalSize == 0) {
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
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("ImmutableSegmentedList[size=").append(totalSize).append(", segCap=").append(segmentCapacity)
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
    throw new UnsupportedOperationException("ImmutableSegmentedList is immutable");
  }

  @Override
  public boolean addAll(Collection<? extends E> c) {
    throw new UnsupportedOperationException("ImmutableSegmentedList is immutable");
  }

  @Override
  public boolean remove(Object o) {
    throw new UnsupportedOperationException("ImmutableSegmentedList is immutable");
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    throw new UnsupportedOperationException("ImmutableSegmentedList is immutable");
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    throw new UnsupportedOperationException("ImmutableSegmentedList is immutable");
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException("ImmutableSegmentedList is immutable");
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
}
