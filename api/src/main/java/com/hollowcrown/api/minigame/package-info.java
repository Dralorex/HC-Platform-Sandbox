/**
 * Hollow Crown minigame integration contracts.
 *
 * <h2>New minigame plugin — link checklist</h2>
 * <ol>
 *   <li><b>Id</b>: define one {@link com.hollowcrown.api.minigame.MinigamePluginKey} (e.g.
 *       {@link com.hollowcrown.api.minigame.MinigamePluginKey#hollowCrown(String, String)}). The
 *       {@link com.hollowcrown.api.ids.MechanicId} string must match Hub template {@code mechanicId} rows.</li>
 *   <li><b>Maven</b>: depend on {@code hollowcrown-api} (scope {@code provided}) + Paper API {@code provided}.</li>
 *   <li><b>plugin.yml</b>: {@code depend: [HC-Foundation, HollowCrownHub]} (or the plugin names stored on your key).</li>
 *   <li><b>Enable</b>: after Hub has registered {@link com.hollowcrown.api.platform.GamePlatform}, call
 *       {@link com.hollowcrown.api.minigame.MinigameBootstrap#registerMechanic} with your
 *       {@link com.hollowcrown.api.mechanic.MinigameMechanic} implementation.</li>
 *   <li><b>Disable</b>: {@link com.hollowcrown.api.minigame.MinigameBootstrap#unregisterMechanic}.</li>
 *   <li><b>Optional</b>: register extra services (e.g. {@link com.hollowcrown.api.integration.IronLegacyHubBridge}
 *       for Hub→minigame callbacks); resolve {@link com.hollowcrown.api.integration.HubWorldApi} and
 *       {@link com.hollowcrown.api.integration.HubPlayerMenus} from {@code ServicesManager} for shared hub-world keys,
 *       limbo hotbars, and Hub GUIs.</li>
 * </ol>
 */
package com.hollowcrown.api.minigame;
