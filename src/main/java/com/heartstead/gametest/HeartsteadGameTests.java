package com.heartstead.gametest;

/**
 * GameTest entrypoint — headless, in-world, automated verification.
 *
 * <p>This is the single biggest practical win from being a mod rather than a data pack. Run with:
 *
 * <pre>./gradlew runGametest</pre>
 *
 * <h2>How the API works in 26.2</h2>
 *
 * This is a <b>plain class</b> registered under the {@code fabric-gametest} entrypoint in
 * {@code fabric.mod.json}. There is no interface to implement — the old {@code FabricGameTest}
 * interface is gone. Tests are public methods annotated with
 * {@link net.fabricmc.fabric.api.gametest.v1.GameTest}, which carries {@code structure},
 * {@code maxTicks}, {@code setupTicks}, {@code rotation}, {@code skyAccess}, {@code maxAttempts}
 * and friends.
 *
 * <h2>What belongs here</h2>
 *
 * In rough priority order (see {@code docs/DESIGN.md}):
 * <ul>
 *   <li><b>§2.5 Vault item conservation</b> — the highest-value tests in the project. Deposit N,
 *       withdraw N, assert nothing was created or destroyed. Include the nasty cases: server
 *       shutdown mid-transfer, full inventory on withdraw, capacity boundary, concurrent access.
 *       The data pack version of this system could only be tested by hand and was expected to eat
 *       someone's inventory; here it does not have to be.</li>
 *   <li><b>§4.4 core offline accrual</b> — advance the world clock, unload and reload the chunk,
 *       assert yield matches the elapsed-time formula and does not double-count.</li>
 *   <li><b>§4.4 per-player core caps</b> — assert the cap actually blocks placement.</li>
 *   <li><b>§7.5 villager trade persistence</b> — level up, restock, reload, assert the taught offer
 *       survives all three.</li>
 *   <li><b>§5 lives floor</b> — die repeatedly, assert health never drops below the configured floor.</li>
 * </ul>
 *
 * <p>Pure logic with no world dependency belongs in {@code src/test} as plain JUnit instead — it
 * runs in milliseconds and does not need a server.
 */
public class HeartsteadGameTests {

    // TODO Phase 3: vault conservation suite — write these BEFORE the Vault itself.
}
