/*
 * Copyright (c) 2011 ScalaStuff.org (joint venture of Alexander Dvorkovyy and Ruud Diterwich)
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.scalabeans.stuff
package value

import org.scalabeans.Preamble._
import com.dyuproject.protostuff.{Pipe, Input, Output}
import org.scalabeans.{ScalaType, MutablePropertyDescriptor, ConstructorParameter, BeanDescriptor}

abstract class BeanValueHandler extends ValueHandler {
  type V = AnyRef

  def writeSchema: WriteMirrorSchema[V]

  def readSchema: MirrorSchema[_]

  override def isDefaultValue(v: V) = false

  def writeValueTo(tag: Int, output: Output, value: V, repeated: Boolean) {
    output.writeObject(tag, value, writeSchema, repeated)
  }

  def transfer(tag: Int, pipe: Pipe, input: Input, output: Output, repeated: Boolean) {
    output.writeObject(tag, pipe, writeSchema.pipeSchema, repeated)
  }
}

object BeanValueHandler {
  def apply(beanType: ScalaType) = {
    val beanDescriptor = descriptorOf(beanType)
    if (beanDescriptor.hasImmutableConstructorParameters) new ImmutableBeanValueHandler(beanDescriptor)
    else new MutableBeanValueHandler(beanType)
  }
}

class MutableBeanValueHandler(beanType: ScalaType) extends BeanValueHandler {

  override lazy val writeSchema = MirrorSchema.schemaOf[V](beanType)

  def readSchema = writeSchema

  def defaultValue = {
    val value = writeSchema.newMessage()
    writeSchema.resetAllFieldsToDefault(value)
    value
  }

  def readFrom(input: Input) = input.mergeObject(null, writeSchema)
}

class ImmutableBeanValueHandler(beanDescriptor: BeanDescriptor) extends BeanValueHandler {
  def writeSchema = readSchema.writeSchema
  lazy val readSchema = BeanBuilderSchema(beanDescriptor)

  override def defaultValue = {
    val builder = readSchema.newMessage
    for (field <- readSchema.fields)
      field.setDefaultValue(builder)

    builder.result()
  }

  override def readFrom(input: Input) = {
    val builder = input.mergeObject(null, readSchema)
    builder.result()
  }
}