# Focused manual gap checks

These development-only conditions supplement the existing continuous checks. They do not drive input, create protocol authority or change the public resource fixture. All gameplay is manual. The input pilot remains disabled/unqualified. Commands below identify bundle assets, not permission to launch or run them.

## Order and startup

Use a reviewed clean successor checkpoint with fresh negotiated startup and publication observers enabled. Run the epoch seed before ANY cast: it requires input/use0 at startup. Order: epoch seed → real save/rejoin → epoch target → retired publication → fresh use of the same replacement → pair fault → real reconnect → funded recovery. Read/verify each stateful gate before the next block. Do not run the startup/reset/teardown bundle between epoch seed and target: explicit reset clears the capsule. Do not exit the client JVM or use another world/server during that one reconnect.

Existing bundles provide setup/inspection. New setup names are `cycle007_terminal_gap_epoch_seed_setup`, `cycle007_terminal_gap_epoch_target_setup`, and `cycle007_terminal_gap_retired_setup`. P11 continues using `cycle007_terminal_p11_pair_fault_setup`. Source commands remain in continuous-bundles.json. Do not hot-copy these assets or compile underneath a running client.

## Epoch seed and target

Seed: one ordinary main-hand sky-aimed hold about1second/release/wait for ordinary cooldown/inspect. Wizardry's actual server Post callback fires once at cast start, not on every continuous effect. That single callback requests eight consecutive real routine maxCooldown/ordinary Container publications: seven display-maximum changes followed by restoring/publishing the original maximum in finally. Current cooldown is never changed (even transiently), so the seed stays an ordinary continuing cast; all final wand data is restored. These are eight publications, NOT eight spell effects; no additional effect/payment is requested and no metadata/serial is invented. The actual captured serial must still exceed the next session's actual watermark or the target is UNREACHED. Inspect must show a complete matching publication/normal-close/release capsule and empty live queues; retain its digest and tuple before disconnect.

After agent inspection, Save and Quit/rejoin the same disposable world, then inspect ONLY. Verify fresh real capability/epoch/clear state and same byte-only capsule before target setup. One bounded capsule survives exactly this disconnect, for at most15minutes; expiration/missing member/wrong owner/extra disconnect is UNREACHED. Maximum64KiB combined metadata+single-slot packet body,256byte production metadata limit,512bytes each for close/release; no retained live world/player/network/packet/buffer.

Target: one normal hold about2seconds/release/inspect; the helper replays saved bytes at the first actual new effect, automatically while held. Actual old/current epoch must differ; the old serial must exceed the actual current receive watermark, and close/release identifiers must challenge the fresh use. Require real receiver return=false for the replayed vanilla instance, no main apply/install of it, actual client close accept=false and actual server release=false/unchanged watermark. New effects continue and only the new use's own normal release/close produces its Finish/cooldown/payment totals. Capsule must be consumed, not reset away. Verify actual IDs; timing assumptions or helper labels cannot pass the card.

## Retired publication

One normal hold/release/inspect. At real pending capture, the helper forces an actual old-source publication, holds its whole pair/real close, then performs the existing byte-equal actual server replacement. Only actual client main-thread retirement releases the saved old pair and close; receiving the retirement marker alone is insufficient. Require no stale apply/transfer/old client Finish. Record any serial-rejection contribution honestly.

After the agent verifies the healthy end, make one fresh normal hold/release on that same replacement without setup, reseed or reconnect. Verify distinct use/newer generation, ordinary Finish/cooldown and resource deltas. Resetting/replacing the wand for this fresh use would lose the discriminator.

## Pair fault

Reuse the existing one-cast fault setup. Match real partner suppression, production fault, exact unrelated held-slot packet receive and its actual post-assignment observation while faulted. The assignment is in WandInventoryMutations.select after the production redirect, not the original handler field instruction. A scoped client handler token links the write to the received packet and clears in finally. A same-value write is acceptable only with the actual assignment trace; unchanged selected slot alone is not proof. No client Finish/extra payment.

Retained same-connection rejection remains applicable only if review confirms fault/control behavior unchanged. Verify real reconnect/new epoch before funded setup/cast/recovery. Fault cannot be cleared with reset. Original independent fixture/resource/Finish evidence remains required.

## Reading evidence

`verify-gap-observations.ps1 -Case epoch|retired|fault -LogPath <retained-log> -StartMarker <exact-case-ready-mark>` slices the last exact start mark to its next inspect mark and checks only the new observation boundary. Its result explicitly says **not card acceptance**. Retain separate raw source/capsule hashes, actual startup/setup/action/owner verdict, authoritative effects/resources/Finish/cooldown and cleanup/recovery analysis. Never use these observation checks as a replacement for the existing evidence reader or full dedicated topology.

`test-gap-observation-reader.ps1` uses synthetic strings to test missing observations/receiver application/wrong identity/epoch-masking/watermark mutations. These are reader sensitivity, not runtime evidence. Unit/Netty/selector tests likewise cannot prove live Mixins or socket behavior. Review and a separately approved replacement checkpoint/fresh P0 precede gameplay. No shipping/API or input-harness change is included.
