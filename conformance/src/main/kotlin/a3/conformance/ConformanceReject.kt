package a3.conformance

class ConformanceReject(val code: String, reason: String) : IllegalArgumentException("$code: $reason")
