package com.facebook.react.internal

import android.os.Build
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue

/**
 * Thread-safe [Queue] for producer/consumer use. Android 12 has a problematic
 * [ConcurrentLinkedQueue], so fall back to [LinkedBlockingQueue] instead.
 * See https://issuetracker.google.com/issues/261481042.
 */
public object QueueCompat {

  @JvmStatic
  public fun <E> create(): Queue<E> =
      if (isAffectedRuntime()) LinkedBlockingQueue() else ConcurrentLinkedQueue()

  @JvmStatic
  public fun isAffectedRuntime(): Boolean = Build.VERSION.SDK_INT == Build.VERSION_CODES.S
}
