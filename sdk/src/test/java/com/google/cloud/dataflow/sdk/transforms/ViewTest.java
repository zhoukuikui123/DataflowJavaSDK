/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.dataflow.sdk.transforms;

import static org.hamcrest.CoreMatchers.isA;

import com.google.cloud.dataflow.sdk.Pipeline;
import com.google.cloud.dataflow.sdk.coders.VarIntCoder;
import com.google.cloud.dataflow.sdk.coders.VoidCoder;
import com.google.cloud.dataflow.sdk.testing.DataflowAssert;
import com.google.cloud.dataflow.sdk.testing.TestPipeline;
import com.google.cloud.dataflow.sdk.transforms.windowing.FixedWindows;
import com.google.cloud.dataflow.sdk.transforms.windowing.GlobalWindows;
import com.google.cloud.dataflow.sdk.transforms.windowing.Window;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.cloud.dataflow.sdk.values.PCollection;
import com.google.cloud.dataflow.sdk.values.PCollectionView;
import com.google.cloud.dataflow.sdk.values.TimestampedValue;

import org.joda.time.Duration;
import org.joda.time.Instant;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.Serializable;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Tests for {@link View}. See also {@link ParDoTest} which
 * provides additional coverage since views can only be
 * observed via {@link ParDo}.
 */
@RunWith(JUnit4.class)
@SuppressWarnings("serial")
public class ViewTest implements Serializable {
  // This test is Serializable, just so that it's easy to have
  // anonymous inner classes inside the non-static test methods.

  @Rule
  public transient ExpectedException thrown = ExpectedException.none();

  @Test
  @Category(com.google.cloud.dataflow.sdk.testing.RunnableOnService.class)
  public void testSingletonSideInput() {
    Pipeline pipeline = TestPipeline.create();

    final PCollectionView<Integer> view = pipeline
        .apply(Create.of(47))
        .apply(View.<Integer>asSingleton());

    PCollection<Integer> output = pipeline
        .apply(Create.of(1, 2, 3))
        .apply(ParDo.withSideInputs(view).of(
            new DoFn<Integer, Integer>() {
              @Override
              public void processElement(ProcessContext c) {
                c.output(c.sideInput(view));
              }
            }));

    DataflowAssert.that(output)
        .containsInAnyOrder(47, 47, 47);

    pipeline.run();
  }

  @Test
  public void testEmptySingletonSideInput() throws Exception {
    Pipeline pipeline = TestPipeline.create();

    final PCollectionView<Integer> view = pipeline
        .apply(Create.<Integer>of())
        .setCoder(VarIntCoder.of())
        .apply(View.<Integer>asSingleton());

    PCollection<Integer> output = pipeline
        .apply(Create.of(1, 2, 3))
        .apply(ParDo.withSideInputs(view).of(
            new DoFn<Integer, Integer>() {
              @Override
              public void processElement(ProcessContext c) {
                c.output(c.sideInput(view));
              }
            }));

    thrown.expect(RuntimeException.class);
    thrown.expectCause(isA(NoSuchElementException.class));
    thrown.expectMessage("Empty");
    thrown.expectMessage("PCollection");
    thrown.expectMessage("singleton");

    pipeline.run();
  }

  @Test
  public void testNonSingletonSideInput() throws Exception {
    Pipeline pipeline = TestPipeline.create();

    final PCollectionView<Integer> view = pipeline
        .apply(Create.<Integer>of(1, 2, 3))
        .apply(View.<Integer>asSingleton());

    PCollection<Integer> output = pipeline
        .apply(Create.of(1, 2, 3))
        .apply(ParDo.withSideInputs(view).of(
            new DoFn<Integer, Integer>() {
              @Override
              public void processElement(ProcessContext c) {
                c.output(c.sideInput(view));
              }
            }));

    thrown.expect(RuntimeException.class);
    thrown.expectCause(isA(IllegalArgumentException.class));
    thrown.expectMessage("PCollection");
    thrown.expectMessage("more than one");
    thrown.expectMessage("singleton");

    pipeline.run();
  }

  @Test
  @Category(com.google.cloud.dataflow.sdk.testing.RunnableOnService.class)
  public void testIterableSideInput() {
    Pipeline pipeline = TestPipeline.create();

    final PCollectionView<Iterable<Integer>> view = pipeline
        .apply(Create.of(11, 13, 17, 23))
        .apply(View.<Integer>asIterable());

    PCollection<Integer> output = pipeline
        .apply(Create.of(29, 31))
        .apply(ParDo.withSideInputs(view).of(
            new DoFn<Integer, Integer>() {
              @Override
              public void processElement(ProcessContext c) {
                for (Integer i : c.sideInput(view)) {
                  c.output(i);
                }
              }
            }));

    DataflowAssert.that(output).containsInAnyOrder(
        11, 13, 17, 23,
        11, 13, 17, 23);

    pipeline.run();
  }

  @Test
  @Category(com.google.cloud.dataflow.sdk.testing.RunnableOnService.class)
  public void testMapSideInput() {
    Pipeline pipeline = TestPipeline.create();

    final PCollectionView<Map<String, Iterable<Integer>>> view = pipeline
        .apply(Create.of(KV.of("a", 1), KV.of("a", 2), KV.of("b", 3)))
        .apply(View.<String, Integer>asMap());

    PCollection<KV<String, Integer>> output = pipeline
        .apply(Create.of("apple", "banana", "blackberry"))
        .apply(ParDo.withSideInputs(view).of(
            new DoFn<String, KV<String, Integer>>() {
              @Override
              public void processElement(ProcessContext c) {
                for (Integer v : c.sideInput(view).get(c.element().substring(0, 1))) {
                  c.output(KV.of(c.element(), v));
                }
              }
            }));

    DataflowAssert.that(output)
        .containsInAnyOrder(KV.of("apple", 1), KV.of("apple", 2),
                            KV.of("banana", 3), KV.of("blackberry", 3));

    pipeline.run();
  }

  @Test
  @Category(com.google.cloud.dataflow.sdk.testing.RunnableOnService.class)
  public void testSingletonMapSideInput() {
    Pipeline pipeline = TestPipeline.create();

    final PCollectionView<Map<String, Integer>> view = pipeline
        .apply(Create.of(KV.of("a", 1), KV.of("b", 3)))
        .apply(View.<String, Integer>asMap().withSingletonValues());

    PCollection<KV<String, Integer>> output = pipeline
        .apply(Create.of("apple", "banana", "blackberry"))
        .apply(ParDo.withSideInputs(view).of(
            new DoFn<String, KV<String, Integer>>() {
              @Override
              public void processElement(ProcessContext c) {
                c.output(KV.of(c.element(), c.sideInput(view).get(c.element().substring(0, 1))));
              }
            }));

    DataflowAssert.that(output)
        .containsInAnyOrder(KV.of("apple", 1),
                            KV.of("banana", 3), KV.of("blackberry", 3));

    pipeline.run();
  }

  @Test
  @Category(com.google.cloud.dataflow.sdk.testing.RunnableOnService.class)
  public void testCombinedMapSideInput() {
    Pipeline pipeline = TestPipeline.create();

    final PCollectionView<Map<String, Integer>> view = pipeline
        .apply(Create.of(KV.of("a", 1), KV.of("a", 20), KV.of("b", 3)))
        .apply(View.<String, Integer>asMap().withCombiner(new Sum.SumIntegerFn()));

    PCollection<KV<String, Integer>> output = pipeline
        .apply(Create.of("apple", "banana", "blackberry"))
        .apply(ParDo.withSideInputs(view).of(
            new DoFn<String, KV<String, Integer>>() {
              @Override
              public void processElement(ProcessContext c) {
                c.output(KV.of(c.element(), c.sideInput(view).get(c.element().substring(0, 1))));
              }
            }));

    DataflowAssert.that(output)
        .containsInAnyOrder(KV.of("apple", 21),
                            KV.of("banana", 3), KV.of("blackberry", 3));

    pipeline.run();
  }

  @Test
  @Category(com.google.cloud.dataflow.sdk.testing.RunnableOnService.class)
  public void testWindowedSideInputFixedToFixed() {
    Pipeline p = TestPipeline.create();

    final PCollectionView<Integer> view = p
        .apply(Create.timestamped(
            TimestampedValue.of(1, new Instant(1)),
            TimestampedValue.of(2, new Instant(11)),
            TimestampedValue.of(3, new Instant(13))))
        .apply(Window.<Integer>into(FixedWindows.of(Duration.millis(10))))
        .apply(Sum.integersGlobally().withoutDefaults())
        .apply(View.<Integer>asSingleton());

    PCollection<String> output = p
        .apply(Create.timestamped(
            TimestampedValue.of("A", new Instant(4)),
            TimestampedValue.of("B", new Instant(15)),
            TimestampedValue.of("C", new Instant(7))))
        .apply(Window.<String>into(FixedWindows.of(Duration.millis(10))))
        .apply(ParDo.withSideInputs(view).of(
            new DoFn<String, String>() {
              @Override
              public void processElement(ProcessContext c) {
                c.output(c.element() + c.sideInput(view));
              }
            }));

    DataflowAssert.that(output).containsInAnyOrder("A1", "B5", "C1");

    p.run();
  }

  @Test
  @Category(com.google.cloud.dataflow.sdk.testing.RunnableOnService.class)
  public void testWindowedSideInputFixedToGlobal() {
    Pipeline p = TestPipeline.create();

    final PCollectionView<Integer> view = p
        .apply(Create.timestamped(
            TimestampedValue.of(1, new Instant(1)),
            TimestampedValue.of(2, new Instant(11)),
            TimestampedValue.of(3, new Instant(13))))
        .apply(Window.<Integer>into(new GlobalWindows()))
        .apply(Sum.integersGlobally())
        .apply(View.<Integer>asSingleton());

    PCollection<String> output = p
        .apply(Create.timestamped(
            TimestampedValue.of("A", new Instant(4)),
            TimestampedValue.of("B", new Instant(15)),
            TimestampedValue.of("C", new Instant(7))))
        .apply(Window.<String>into(FixedWindows.of(Duration.millis(10))))
        .apply(ParDo.withSideInputs(view).of(
            new DoFn<String, String>() {
              @Override
              public void processElement(ProcessContext c) {
                c.output(c.element() + c.sideInput(view));
              }
            }));

    DataflowAssert.that(output).containsInAnyOrder("A6", "B6", "C6");

    p.run();
  }

  @Test
  @Category(com.google.cloud.dataflow.sdk.testing.RunnableOnService.class)
  public void testWindowedSideInputFixedToFixedWithDefault() {
    Pipeline p = TestPipeline.create();

    final PCollectionView<Integer> view = p
        .apply(Create.timestamped(
            TimestampedValue.of(2, new Instant(11)),
            TimestampedValue.of(3, new Instant(13))))
        .apply(Window.<Integer>into(FixedWindows.of(Duration.millis(10))))
        .apply(Sum.integersGlobally().asSingletonView());

    PCollection<String> output = p
        .apply(Create.timestamped(
            TimestampedValue.of("A", new Instant(4)),
            TimestampedValue.of("B", new Instant(15)),
            TimestampedValue.of("C", new Instant(7))))
        .apply(Window.<String>into(FixedWindows.of(Duration.millis(10))))
        .apply(ParDo.withSideInputs(view).of(
            new DoFn<String, String>() {
              @Override
              public void processElement(ProcessContext c) {
                c.output(c.element() + c.sideInput(view));
              }
            }));

    DataflowAssert.that(output).containsInAnyOrder("A0", "B5", "C0");

    p.run();
  }

  @Test
  @Category(com.google.cloud.dataflow.sdk.testing.RunnableOnService.class)
  public void testSideInputWithNullDefault() {
    Pipeline p = TestPipeline.create();

    final PCollectionView<Void> view = p
        .apply(Create.of((Void) null)).setCoder(VoidCoder.of())
        .apply(Combine.globally(new SerializableFunction<Iterable<Void>, Void>() {
                  @Override
                  public Void apply(Iterable<Void> input) {
                    return (Void) null;
                  }
                }).asSingletonView());

    PCollection<String> output = p
        .apply(Create.of(""))
        .apply(ParDo.withSideInputs(view).of(
            new DoFn<String, String>() {
              @Override
              public void processElement(ProcessContext c) {
                c.output(c.element() + c.sideInput(view));
              }
            }));

    DataflowAssert.that(output).containsInAnyOrder("null");

    p.run();
  }

}
