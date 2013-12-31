/*Copyright 2013 sumito3478 <sumito3478@gmail.com>

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package saare

import org.scalatest._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class DisposableSpec extends WordSpec with Logging[DisposableSpec] {
  import Saare._
  "Saare.disposing" should {
    "dispose the object asynchronously" in {
      var a = false
      class A extends Disposable[A] {
        override def disposeInternal = a = true
      }
      val f = ((a: A) => scala.concurrent.future {
        ()
      }) |> new A #|> disposing[A, Unit]
      Await.result(f, Duration.Inf)
      assert(a === true)
    }
  }
}