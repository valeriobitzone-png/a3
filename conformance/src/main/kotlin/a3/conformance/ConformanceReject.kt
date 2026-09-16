// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.conformance

class ConformanceReject(val code: String, reason: String) : IllegalArgumentException("$code: $reason")
