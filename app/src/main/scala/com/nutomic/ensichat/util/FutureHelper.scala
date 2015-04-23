package com.nutomic.ensichat.util

import android.util.Log

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Use this instead of [[Future]], to make sure exceptions are logged.
 *
 * @see https://github.com/saturday06/gradle-android-scala-plugin/issues/56
 */
object FutureHelper {

  private val Tag = "FutureHelper"

  def spawn[A](action: => A) = Future(action).onFailure { case e =>
    Log.w(Tag, "Error in future", e)
  }

}
