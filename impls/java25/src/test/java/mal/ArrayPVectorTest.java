/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static mal.ArrayPVector.MAX_SIZE;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.pcollections.PVector;
import org.pcollections.TreePVector;

class ArrayPVectorTest {

  @Test
  void empty() {
    PVector<String> empty = ArrayPVector.empty();

    assertAll(
        () -> assertEquals(0, empty.size()),
        () -> assertEquals(List.of(), empty),
        () -> assertSame(empty, ArrayPVector.from(List.of())),
        () -> assertThrows(IndexOutOfBoundsException.class, () -> empty.get(0)));
  }

  @Test
  void fromSmallIsArray() {
    var vector = ArrayPVector.from(values(MAX_SIZE));

    assertAll(
        () -> assertInstanceOf(ArrayPVector.class, vector),
        () -> assertEquals(values(MAX_SIZE), vector));
  }

  @Test
  void fromBigIsTree() {
    var vector = ArrayPVector.from(values(MAX_SIZE + 1));

    assertAll(
        () -> assertInstanceOf(TreePVector.class, vector),
        () -> assertEquals(values(MAX_SIZE + 1), vector));
  }

  @Test
  void fromCopiesTheCollection() {
    var source = new ArrayList<>(values(3));
    var vector = ArrayPVector.from(source);

    source.set(0, "changed");
    source.add("added");

    assertEquals(values(3), vector);
  }

  @Test
  void plus() {
    var vector = ArrayPVector.from(values(2));

    assertAll(
        () -> assertEquals(List.of("v0", "v1", "x"), vector.plus("x")),
        () -> assertEquals(List.of("x", "v0", "v1"), vector.plus(0, "x")),
        () -> assertEquals(List.of("v0", "x", "v1"), vector.plus(1, "x")),
        () -> assertEquals(List.of("v0", "v1", "x"), vector.plus(2, "x")),
        () -> assertThrows(IndexOutOfBoundsException.class, () -> vector.plus(3, "x")),
        () -> assertThrows(IndexOutOfBoundsException.class, () -> vector.plus(-1, "x")),
        // persistent, the original is not changed
        () -> assertEquals(values(2), vector));
  }

  @Test
  void plusPastMaxSizeIsTree() {
    var full = ArrayPVector.from(values(MAX_SIZE));

    var appended = full.plus("x");
    var inserted = full.plus(0, "x");

    assertAll(
        () -> assertInstanceOf(TreePVector.class, appended),
        () -> assertInstanceOf(TreePVector.class, inserted),
        () -> assertEquals(TreePVector.from(values(MAX_SIZE)).plus("x"), appended),
        () -> assertEquals(TreePVector.from(values(MAX_SIZE)).plus(0, "x"), inserted),
        () -> assertInstanceOf(ArrayPVector.class, full),
        () -> assertEquals(values(MAX_SIZE), full));
  }

  @Test
  void plusAll() {
    var vector = ArrayPVector.from(values(2));

    assertAll(
        () -> assertEquals(List.of("v0", "v1", "x", "y"), vector.plusAll(List.of("x", "y"))),
        () -> assertEquals(List.of("x", "y", "v0", "v1"), vector.plusAll(0, List.of("x", "y"))),
        () -> assertEquals(List.of("v0", "x", "y", "v1"), vector.plusAll(1, List.of("x", "y"))),
        () -> assertSame(vector, vector.plusAll(List.of())),
        () -> assertThrows(IndexOutOfBoundsException.class, () -> vector.plusAll(3, List.of("x"))),
        () -> assertEquals(values(2), vector));
  }

  @Test
  void plusAllPastMaxSizeIsTree() {
    var vector = ArrayPVector.from(values(MAX_SIZE - 1));

    var result = vector.plusAll(1, List.of("x", "y"));

    assertAll(
        () -> assertInstanceOf(TreePVector.class, result),
        () -> assertEquals(TreePVector.from(values(MAX_SIZE - 1)).plusAll(1, List.of("x", "y")), result));
  }

  @Test
  void with() {
    var vector = ArrayPVector.from(values(3));

    assertAll(
        () -> assertEquals(List.of("v0", "x", "v2"), vector.with(1, "x")),
        () -> assertThrows(IndexOutOfBoundsException.class, () -> vector.with(3, "x")),
        () -> assertEquals(values(3), vector));
  }

  @Test
  void minusIndex() {
    var vector = ArrayPVector.from(values(3));

    assertAll(
        () -> assertEquals(List.of("v1", "v2"), vector.minus(0)),
        () -> assertEquals(List.of("v0", "v2"), vector.minus(1)),
        () -> assertEquals(List.of("v0", "v1"), vector.minus(2)),
        () -> assertEquals(List.of(), ArrayPVector.from(List.of("v0")).minus(0)),
        () -> assertThrows(IndexOutOfBoundsException.class, () -> vector.minus(3)),
        () -> assertEquals(values(3), vector));
  }

  @Test
  void minusElement() {
    var vector = ArrayPVector.from(List.of("a", "b", "a"));

    assertAll(
        // only the first occurrence is removed, like in TreePVector
        () -> assertEquals(List.of("b", "a"), vector.minus("a")),
        () -> assertSame(vector, vector.minus("missing")));
  }

  @Test
  void minusAll() {
    var vector = ArrayPVector.from(List.of("a", "b", "a", "c"));

    assertAll(
        // only the first occurrence of each element is removed, like in TreePVector
        () -> assertEquals(List.of("b", "a", "c"), vector.minusAll(List.of("a"))),
        () -> assertEquals(List.of("b", "c"), vector.minusAll(List.of("a", "a"))),
        () -> assertEquals(TreePVector.from(List.of("a", "b", "a", "c")).minusAll(List.of("a")), vector.minusAll(List.of("a"))),
        () -> assertSame(vector, vector.minusAll(List.of("missing"))));
  }

  @Test
  void subList() {
    var vector = ArrayPVector.from(values(4));

    assertAll(
        () -> assertEquals(List.of("v1", "v2"), vector.subList(1, 3)),
        () -> assertEquals(List.of(), vector.subList(2, 2)),
        () -> assertSame(vector, vector.subList(0, 4)),
        () -> assertThrows(IndexOutOfBoundsException.class, () -> vector.subList(0, 5)),
        () -> assertThrows(IndexOutOfBoundsException.class, () -> vector.subList(3, 2)));
  }

  @Test
  void equalsAndHashCode() {
    var vector = ArrayPVector.from(values(3));
    var tree = TreePVector.from(values(3));

    assertAll(
        () -> assertEquals(tree, vector),
        () -> assertEquals(vector, tree),
        () -> assertEquals(values(3), vector),
        () -> assertEquals(tree.hashCode(), vector.hashCode()));
  }

  @Test
  void iterator() {
    var vector = ArrayPVector.from(values(3));
    var iterator = vector.iterator();

    assertAll(
        () -> assertEquals(values(3), new ArrayList<>(vector)),
        () -> assertEquals("v0", iterator.next()),
        () -> assertThrows(UnsupportedOperationException.class, iterator::remove));
  }

  @SuppressWarnings("deprecation")
  @Test
  void isUnmodifiable() {
    var vector = ArrayPVector.from(values(3));

    assertAll(
        () -> assertThrows(UnsupportedOperationException.class, () -> vector.add("x")),
        () -> assertThrows(UnsupportedOperationException.class, () -> vector.set(0, "x")),
        () -> assertThrows(UnsupportedOperationException.class, () -> vector.remove(0)),
        () -> assertThrows(UnsupportedOperationException.class, vector::clear),
        () -> assertEquals(values(3), vector));
  }

  private static List<String> values(int size) {
    return IntStream.range(0, size).mapToObj(i -> "v" + i).toList();
  }
}
