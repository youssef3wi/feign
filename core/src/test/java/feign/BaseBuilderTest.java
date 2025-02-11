/*
 * Copyright 2012-2024 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.RETURNS_MOCKS;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BaseBuilderTest {

  @Test
  void checkEnrichTouchesAllAsyncBuilderFields()
      throws IllegalArgumentException, IllegalAccessException {
    test(AsyncFeign.builder().requestInterceptor(template -> {
    }).responseInterceptor((ic, c) -> c.next(ic)), 14);
  }

  private void test(BaseBuilder<?, ?> builder, int expectedFieldsCount)
      throws IllegalArgumentException, IllegalAccessException {
    Capability mockingCapability = Mockito.mock(Capability.class, RETURNS_MOCKS);
    BaseBuilder<?, ?> enriched = builder.addCapability(mockingCapability).enrich();

    List<Field> fields = enriched.getFieldsToEnrich();
    assertThat(fields).hasSize(expectedFieldsCount);

    for (Field field : fields) {
      field.setAccessible(true);
      Object mockedValue = field.get(enriched);
      if (mockedValue instanceof List) {
        assertThat((List) mockedValue).withFailMessage("Enriched list missing contents %s", field)
            .isNotEmpty();
        mockedValue = ((List<Object>) mockedValue).get(0);
      }
      assertThat(Mockito.mockingDetails(mockedValue)
          .isMock()).as("Field was not enriched " + field).isTrue();
      assertNotSame(builder, enriched);
    }

  }

  @Test
  void checkEnrichTouchesAllBuilderFields()
      throws IllegalArgumentException, IllegalAccessException {
    test(Feign.builder().requestInterceptor(template -> {
    }).responseInterceptor((ic, c) -> c.next(ic)), 12);
  }

  @Test
  void concurrentAccessToInterceptors() throws IllegalAccessException, InterruptedException {
    // Given
    int numberOfIterations = 5000;
    int numberOfThreads = Runtime.getRuntime().availableProcessors();
    ExecutorService service = Executors.newFixedThreadPool(numberOfThreads);
    CountDownLatch latch = new CountDownLatch(numberOfThreads);
    AsyncFeign.AsyncBuilder<?> builder = AsyncFeign.builder();

    // When
    for (int idx = 0; idx < numberOfIterations; idx++) { // to simulate increase iterations
      service.submit(() -> {
        try {
          setupBuilder(builder);
        } catch (InterruptedException ignored) {}

        latch.countDown();
      });
    }

    // Then
    latch.await();
    test(builder, 14);
  }

  private synchronized void setupBuilder(BaseBuilder<?, ?> builder) throws InterruptedException {
    builder.requestInterceptor(template -> {});
    wait(10);
    builder.responseInterceptor((ic, c) -> c.next(ic));
  }

}
