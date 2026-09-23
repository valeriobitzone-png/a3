# The tool said "sent". Nothing was sent.

```bash
python3 examples/receipt-is-not-fact/demo.py          # the story
python3 examples/receipt-is-not-fact/demo.py --json   # the same runs as data
```

Python 3.9+, standard library only, under a second, nothing leaves your machine. The "world" is a temporary folder standing in for a mail server's outbox.

| Run | Agent | Tool | What happens |
|-----|-------|------|--------------|
| 1 | naive | buggy: answers `{"ok": true}`, sends nothing | the agent records FACT from the receipt and says "sent"; the outbox is empty |
| 2 | with A3 | same buggy tool | FACT from the receipt is rejected (CF-001); the receipt is kept as OBSERVATION; the agent checks the outbox, finds nothing, and does not say "sent" |
| 3 | with A3 | working tool | FACT from the receipt is still rejected; the agent checks the outbox, finds the message, and records FACT on that verification |

The rule behind it is SPEC_A3-EP section 3: *An implementation MUST NOT emit FACT from an execution receipt.* It holds whatever the receipt contains; see `conformance/fixtures/receipt-forms.json`.

`expected_output.txt` is the exact output. `python3 python/tests/test_demo.py` checks that the demo still produces it and exits 0.
