/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package mal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ImmutableListTest {

  @Test
  void testEmptyList() {
    ImmutableList<Integer> list = ImmutableList.empty(4);
    assertTrue(list.isEmpty());
    assertEquals(0, list.size());
  }

  @Test
  void testBuilderAppend() {
    ImmutableList.Builder<Integer> builder = ImmutableList.builder(4);
    builder.append(10);
    builder.append(20);
    builder.append(30);
    ImmutableList<Integer> list = builder.build();

    assertEquals(3, list.size());
    assertEquals(10, list.get(0));
    assertEquals(20, list.get(1));
    assertEquals(30, list.get(2));
  }

  @Test
  void testBuilderAppendAll() {
    ImmutableList.Builder<Integer> builder = ImmutableList.builder(4);
    builder.appendAll(List.of(10, 20, 30));
    ImmutableList<Integer> list = builder.build();

    assertEquals(3, list.size());
    assertEquals(10, list.get(0));
    assertEquals(20, list.get(1));
    assertEquals(30, list.get(2));
  }

  @Test
  void testBuilderPrepend() {
    ImmutableList.Builder<Integer> builder = ImmutableList.builder(4);
    builder.prepend(10);
    builder.prepend(20);
    builder.prepend(30);
    ImmutableList<Integer> list = builder.build();

    assertEquals(3, list.size());
    assertEquals(30, list.get(0));
    assertEquals(20, list.get(1));
    assertEquals(10, list.get(2));
  }

  @Test
  void testBuilderPrependAll() {
    ImmutableList.Builder<Integer> builder = ImmutableList.builder(4);
    builder.prependAll(List.of(10, 20, 30));
    ImmutableList<Integer> list = builder.build();

    assertEquals(3, list.size());
    assertEquals(30, list.get(0));
    assertEquals(20, list.get(1));
    assertEquals(10, list.get(2));
  }

  @Test
  void testBuilderMixed() {
    ImmutableList.Builder<Integer> builder = ImmutableList.builder(4);
    builder.append(10);
    builder.prepend(20);
    builder.append(30);
    ImmutableList<Integer> list = builder.build();

    assertEquals(3, list.size());
    assertEquals(20, list.get(0));
    assertEquals(10, list.get(1));
    assertEquals(30, list.get(2));
  }

  @Test
  void testFromCollection() {
    ImmutableList<Integer> list = ImmutableList.from(List.of(10, 20, 30), 4);

    assertEquals(3, list.size());
    assertEquals(10, list.get(0));
    assertEquals(20, list.get(1));
    assertEquals(30, list.get(2));
  }

  @Test
  void testFromStream() {
    ImmutableList<Integer> list = ImmutableList.from(Stream.of(10, 20, 30), 4);

    assertEquals(3, list.size());
    assertEquals(10, list.get(0));
    assertEquals(20, list.get(1));
    assertEquals(30, list.get(2));
  }

  @Test
  void testCollector() {
    ImmutableList<Integer> list = Stream.of(10, 20, 30).collect(ImmutableList.toImmutableList(4));

    assertEquals(3, list.size());
    assertEquals(10, list.get(0));
    assertEquals(20, list.get(1));
    assertEquals(30, list.get(2));
  }

  @Test
  void testAppend() {
    ImmutableList<Integer> list = ImmutableList.empty(4);
    list = list.append(10);
    list = list.append(20);
    list = list.append(30);

    assertEquals(3, list.size());
    assertEquals(10, list.get(0));
    assertEquals(20, list.get(1));
    assertEquals(30, list.get(2));
  }

  @Test
  void testPrepend() {
    ImmutableList<Integer> list = ImmutableList.empty(4);

    list = list.prepend(10);
    list = list.prepend(20);
    list = list.prepend(30);

    assertEquals(3, list.size());
    assertEquals(30, list.get(0));
    assertEquals(20, list.get(1));
    assertEquals(10, list.get(2));
  }

  @Test
  void testConcatenate() {
    ImmutableList<Integer> list1 = ImmutableList.<Integer>empty(4)
      .append(1)
      .append(2);
    ImmutableList<Integer> list2 = ImmutableList.<Integer>empty(4)
      .append(3)
      .append(4);

    ImmutableList<Integer> combined = list1.concat(list2);

    assertEquals(4, combined.size());
    assertEquals(1, combined.get(0));
    assertEquals(2, combined.get(1));
    assertEquals(3, combined.get(2));
    assertEquals(4, combined.get(3));
  }

  @Test
  void testAppendTriggersNewSegment() {
    ImmutableList<Integer> list = ImmutableList.empty(2);
    list = list.append(1);
    list = list.append(2);
    list = list.append(3); // should go to a new segment

    assertEquals(3, list.size());
    assertEquals(1, list.get(0));
    assertEquals(2, list.get(1));
    assertEquals(3, list.get(2));
  }

  @Test
  void testPrependTriggersNewSegment() {
    ImmutableList<Integer> list = ImmutableList.empty(2);
    list = list.prepend(1);
    list = list.prepend(2);
    list = list.prepend(3); // should create a new segment

    assertEquals(3, list.size());
    assertEquals(3, list.get(0));
    assertEquals(2, list.get(1));
    assertEquals(1, list.get(2));
  }

  @Test
  void testRemoveFirst() {
    ImmutableList<Integer> list = ImmutableList.<Integer>empty(3)
      .append(10)
      .append(20)
      .append(30);

    list = list.dropFirst();

    assertEquals(2, list.size());
    assertEquals(20, list.get(0));
    assertEquals(30, list.get(1));
  }

  @Test
  void testRemoveLast() {
    ImmutableList<Integer> list = ImmutableList.<Integer>empty(3)
      .append(10)
      .append(20)
      .append(30);

    list = list.dropLast();

    assertEquals(2, list.size());
    assertEquals(10, list.get(0));
    assertEquals(20, list.get(1));
  }

  @Test
  void testIteration() {
    ImmutableList<Integer> list = ImmutableList.empty(2);
    list = list.append(1).append(2).append(3).append(4).append(5);

    int sum = 0;
    for (int x : list)
      sum += x;

    assertEquals(15, sum);
  }

  @Test
  void testImmutabilitySharing() {
    ImmutableList<Integer> original = ImmutableList.empty(3);
    ImmutableList<Integer> updated = original.append(10);

    assertEquals(0, original.size());
    assertEquals(1, updated.size());
    assertEquals(10, updated.get(0));
    assertTrue(original.isEmpty());
  }

  @Test
  void testComplexSequence() {
    ImmutableList<Integer> list = ImmutableList.empty(3);

    list = list.append(1);
    list = list.append(2);
    list = list.prepend(0);
    list = list.append(3);
    list = list.prepend(-1);
    list = list.dropLast();

    assertEquals(4, list.size());
    assertEquals(-1, list.get(0));
    assertEquals(0, list.get(1));
    assertEquals(1, list.get(2));
    assertEquals(2, list.get(3));
  }
}
