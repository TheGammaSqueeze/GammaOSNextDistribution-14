/*
 * * Copyright 2022 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ijar;

public class SamInJava<T> {
  public final <R> SamInJava<R> fmap(
      Function<? super T, ? extends SamInJava<? extends R>> mapper) {
    return new SamInJava<R>(mapper.run(value).value);
  }

  public SamInJava(T x) {
    this.value = x;
  }

  public final T value;

  @FunctionalInterface
  public interface Function<T, R> {
    R run(T arg);
  }
}
