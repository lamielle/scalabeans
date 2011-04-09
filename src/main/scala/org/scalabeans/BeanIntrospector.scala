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

package org.scalabeans

import java.lang.reflect._
import com.thoughtworks.paranamer.BytecodeReadingParanamer
import collection.JavaConversions._
import Preamble._

object BeanIntrospector {
  def apply[T <: AnyRef](mf: Manifest[_]): BeanDescriptor = apply[T](Preamble.scalaTypeOf(mf))

  def apply[T <: AnyRef](_beanType: ScalaType) = {

    def getTopLevelClass(c: Class[_]): Class[_] = c.getSuperclass match {
      case null => c
      case superClass if superClass == classOf[java.lang.Object] => c
      case superClass => getTopLevelClass(superClass)
    }

    val c = _beanType.erasure

    def classExtent(c: Class[_]): List[Class[_]] = {
      if (c == classOf[AnyRef]) Nil
      else classExtent(c.getSuperclass) :+ c
    }

    /**
     * Constructor. Secondary constructors are not supported
     */
    val constructor: Constructor[_] = {
      require(c.getConstructors().size > 0, c.getName + " has no constructors")
      c.getConstructors()(0).asInstanceOf[Constructor[_]]
    }

    val paranamer = new BytecodeReadingParanamer
    val ctorParameterNames = paranamer.lookupParameterNames(constructor)

    var tag = 0
    var mutablePropertyPosition = 0
    def createPropertyDescriptor(beanType: ScalaType, name: String, field: Option[Field], getter: Option[Method], setter: Option[Method]) = {
      tag = tag + 1

      var ctorIndex = ctorParameterNames.indexOf(name)
      if (ctorIndex < 0) {
        ctorIndex = ctorParameterNames.indexOf("_" + name)

        // check if declared in superclass, otherwise does not allow _name
        if (ctorIndex >= 0) {
          val accessible = field orElse getter get

          if (accessible.getDeclaringClass == beanType.erasure)
            ctorIndex = -1
        }
      }

      val defaultValueMethod =
        if (ctorIndex < 0) None
        else beanType.erasure.getMethods.find(_.getName == "init$default$" + (ctorIndex + 1))

      val descriptor = PropertyDescriptor(beanType, tag, mutablePropertyPosition, field, getter, setter, ctorIndex, defaultValueMethod)
      if (descriptor.isInstanceOf[MutablePropertyDescriptor] && !descriptor.isInstanceOf[ConstructorParameter])
        mutablePropertyPosition += 1

      descriptor
    }

    //
    // Properties of the class.
    //

    /**
     * Searches for the method with given name in the given class. Overridden method discovered if present.
     */
    def findMethod(c: Class[_], name: String): Option[Method] = {
      if (c == null) None
      else if (c == classOf[AnyRef]) None
      else c.getDeclaredMethods.find(_.getName == name) orElse findMethod(c.getSuperclass(), name)
    }

    def typeSupported(scalaType: ScalaType) = {
//      val typeName = scalaType.erasure.getName
//      !(typeName startsWith "java.") &&
//      !(typeName startsWith "scala.")

      true // todo: list supported Java and Scala types ...
    }

    val fieldProperties = for {
      c <- classExtent(c)
      field <- c.getDeclaredFields
      name = field.getName

      if !name.contains('$')
      if !field.isSynthetic
      if typeSupported(scalaTypeOf(field.getGenericType))

      getter = findMethod(_beanType.erasure, name)
      setter = findMethod(_beanType.erasure, name + "_$eq")
    } yield createPropertyDescriptor(_beanType, name, Some(field), getter, setter)

    val methodProperties = for {
      c <- classExtent(c)
      getter <- c.getDeclaredMethods
      name = getter.getName

      if getter.getParameterTypes.length == 0
      if getter.getReturnType != Void.TYPE
      if !name.contains('$')
      if typeSupported(scalaTypeOf(getter.getGenericReturnType))
      if !fieldProperties.exists(_.name == name)
      setter <- c.getDeclaredMethods.find(_.getName == name + "_$eq")

    } yield createPropertyDescriptor(_beanType, name, None, Some(getter), Some(setter))

    new BeanDescriptor {
      val beanType = _beanType
      val properties = fieldProperties ++ methodProperties

      val topLevelClass = getTopLevelClass(c)
    }
  }
  
  def print(c: Class[_], prefix: String = "") : Unit = {
  	def static(mods : Int) = if (Modifier.isStatic(mods)) " (static)" else "" 
  	println(prefix + "Class: " + c.getName)
  	println(prefix + "  Fields: ")
  	for (f <- c.getDeclaredFields) {
  		println(prefix + "    " + f.getName + " : " + f.getGenericType + static(f.getModifiers))
  		if (f.getName == "$outer") print(f.getType, "      ")
  	}
  	println(prefix + "  Methods: ")
  	for (f <- c.getDeclaredMethods) {
  		println(prefix + "    " + f.getName + " : " + f.getGenericReturnType + static(f.getModifiers))
  	}
  	println(prefix + "  Sub classes: ")
  	for (f <- c.getDeclaredClasses) {
  		println(prefix + "    " + f.getName + static(f.getModifiers))
  	}
  	println(prefix + "  Enum Values: ")
  	for (f <- c.getMethods filter (m => m.getParameterTypes.isEmpty && classOf[Enumeration$Value].isAssignableFrom(m.getReturnType))) {
  		val instance = new Enumeration{}
//  		val value = f.invoke(instance).asInstanceOf[Enumeration$Value]
  		println(prefix + "    a" )
  	}
//  	println(prefix + "  Companion:")
//  	print(Class.forName(c.getName + "$"), "      ")
  }
}