// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core.t12

class T12Reject(reason: String) : IllegalArgumentException(reason)

class T12NetworkFault(reason: String) : Exception(reason)
