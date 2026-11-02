package dev.alinco8.xmmp.config

import dev.alinco8.xmmp.client.XMMPClient
import dev.alinco8.xmmp.core.sharing.GlobalSharingConsent
import dev.isxander.yacl3.api.ConfigCategory
import dev.isxander.yacl3.api.LabelOption
import dev.isxander.yacl3.api.Controller
import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.OptionDescription
import dev.isxander.yacl3.api.YetAnotherConfigLib
import dev.isxander.yacl3.api.controller.CyclingListControllerBuilder
import dev.isxander.yacl3.api.controller.ControllerBuilder
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder
import dev.isxander.yacl3.api.controller.LongFieldControllerBuilder
import dev.isxander.yacl3.api.controller.StringControllerBuilder
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty0
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

private fun <T : Any> Option.Builder<T>.bind(
    default: KProperty0<T>,
    current: KMutableProperty0<T>,
) = binding(default.get(), { current.get() }, { current.set(it) })

private fun t(text: String) = Component.translatable("xmmp.config.$text")

private fun <T : Any> ConfigCategory.Builder.simpleOption(
    category: String,
    default: KProperty0<T>,
    current: KMutableProperty0<T>,
    controller: (Option<T>) -> ControllerBuilder<T>,
) = option(
    Option.createBuilder<T>()
        .name(t("categories.$category.options.${current.name}.name"))
        .description(OptionDescription.of(t("categories.$category.options.${current.name}.description")))
        .bind(default, current)
        .controller(controller)
        .build()
)

private data class AdminDraft(
    var allowTeam: Boolean,
    var allowGlobal: Boolean,
    var allowUploads: Boolean,
)

object ConfigScreen {
    @Suppress("MaxLineLength")
    fun createScreen(parent: Screen): Screen {
        val h = XMMPConfig.HANDLER
        val d = h.defaults()
        val i = h.instance()

        val status = XMMPClient.sharingStatus
        val starting = Triple(i.terrainSharingMode, i.receiveTeamTerrain, i.receiveGlobalTerrain)
        val adminDefaults = AdminDraft(status?.allowTeam ?: false,
            status?.allowGlobal ?: false, status?.allowUploads ?: false)
        val adminDraft = adminDefaults.copy()
        // Confirmation is deliberately not persisted: every new GLOBAL publication must be
        // acknowledged in the same settings session. Receiving global terrain does not
        // publish a player's private exploration and needs no publication consent.
        var globalPublicationAcknowledged = false
        var adminGlobalAcknowledged = false
        val statusLabel = when {
            status == null -> "Not connected to an XMMP server"
            status.partyScope.isEmpty() -> "No FTB party / publishing: ${status.activePublishScope.ifEmpty { "disabled" }}"
            else -> "FTB party: ${status.partyScope.removePrefix("team-")} / publishing: ${status.activePublishScope.ifEmpty { "disabled" }}"
        }

        var builder = YetAnotherConfigLib.createBuilder()
            .title(t("title"))
            .save {
                // Never persist or handshake a new GLOBAL publication by a stray click.
                // YACL applies its bound values before invoking this save callback.
                val safeMode = GlobalSharingConsent.publishingMode(
                    starting.first, i.terrainSharingMode, globalPublicationAcknowledged)
                if (safeMode != i.terrainSharingMode) {
                    i.terrainSharingMode = safeMode
                    XMMPClient.showMessage(
                        "GLOBAL publication NOT enabled. Read the PvP/Fog-of-War warning and tick the acknowledgement."
                    )
                }
                // An OP enabling global publication for the whole server must also opt in.
                if (status?.admin == true) {
                    val safeGlobal = GlobalSharingConsent.globalServerEnabled(
                        adminDefaults.allowGlobal, adminDraft.allowGlobal, adminGlobalAcknowledged)
                    if (safeGlobal != adminDraft.allowGlobal) {
                        adminDraft.allowGlobal = safeGlobal
                        XMMPClient.showMessage(
                            "Server GLOBAL sharing NOT enabled. Confirm the PvP/Fog-of-War risk first."
                        )
                    }
                }
                h.save()
                if (starting != Triple(i.terrainSharingMode, i.receiveTeamTerrain, i.receiveGlobalTerrain)) {
                    XMMPClient.session?.onSharingPreferencesSaved()
                }
                if (status?.admin == true && adminDraft != adminDefaults) {
                    XMMPClient.sendAdminSettings(
                        adminDraft.allowTeam, adminDraft.allowGlobal, adminDraft.allowUploads
                    )
                }
            }
            .category(
                ConfigCategory.createBuilder()
                    .name(t("categories.general.name"))
                    .simpleOption(
                        "general",
                        d::flushInterval,
                        i::flushInterval,
                        LongFieldControllerBuilder::create
                    )
                    .simpleOption(
                        "general",
                        d::checkUpdate,
                        i::checkUpdate,
                        TickBoxControllerBuilder::create
                    )
                    .simpleOption(
                        "general",
                        d::downloadWindow,
                        i::downloadWindow,
                        IntegerFieldControllerBuilder::create
                    )
                    .build()
            )

        builder = builder.category(
            ConfigCategory.createBuilder()
                .name(t("categories.sharing.name"))
                .option(
                    LabelOption.createBuilder()
                        .line(t("categories.sharing.warning.headline").copy()
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                        .line(t("categories.sharing.warning.everyone").copy()
                            .withStyle(ChatFormatting.RED))
                        .line(t("categories.sharing.warning.irreversible").copy()
                            .withStyle(ChatFormatting.GOLD))
                        .build()
                )
                .option(
                    Option.createBuilder<String>()
                        .name(t("categories.sharing.options.terrainSharingMode.name"))
                        .description(OptionDescription.of(t("categories.sharing.options.terrainSharingMode.description")))
                        .bind(d::terrainSharingMode, i::terrainSharingMode)
                        .controller { option -> CyclingListControllerBuilder.create(option)
                            .values(listOf("PRIVATE", "TEAM", "GLOBAL"))
                            .formatValue { mode ->
                                if (mode == "GLOBAL")
                                    t("categories.sharing.options.terrainSharingMode.globalDanger")
                                        .copy().withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                                else Component.literal(mode)
                            } }
                        .build()
                )
                .option(
                    Option.createBuilder<Boolean>()
                        .name(t("categories.sharing.options.globalRiskAcknowledged.name")
                            .copy().withStyle(ChatFormatting.RED))
                        .description(OptionDescription.of(
                            t("categories.sharing.options.globalRiskAcknowledged.description")))
                        .binding(false, { globalPublicationAcknowledged },
                            { globalPublicationAcknowledged = it })
                        .controller(TickBoxControllerBuilder::create)
                        .build()
                )
                .simpleOption("sharing", d::receiveTeamTerrain, i::receiveTeamTerrain,
                    TickBoxControllerBuilder::create)
                .simpleOption("sharing", d::receiveGlobalTerrain, i::receiveGlobalTerrain,
                    TickBoxControllerBuilder::create)
                .build()
        )
        // Server-verified status; all counters are per session, not cumulative lifetime totals.
        // Labels are intentionally separate rows rather than one truncated option tooltip.
        builder = builder.category(
            ConfigCategory.createBuilder()
                .name(Component.literal("XMMP Status"))
                .option(
                    LabelOption.createBuilder()
                        .line(Component.literal(statusLabel))
                        .line(Component.literal(
                            "Receive: TEAM=${status?.receiveTeam == true}, GLOBAL=${status?.receiveGlobal == true}"
                        ))
                        .line(Component.literal(
                            "Server uploads stored=${status?.uploadsStored ?: 0}, rejected=${status?.uploadsRejected ?: 0}"
                        ))
                        .line(Component.literal(
                            "Server sent=${status?.downloadPackets ?: 0} tiles, ${(status?.downloadedBytes ?: 0L)/1024L} KB"
                        ))
                        .line(Component.literal(
                            "Regions: requested=${status?.regionRequests ?: 0}, throttled=${status?.regionThrottled ?: 0}"
                        ))
                        .line(Component.literal(XMMPClient.session?.syncDiagnostics() ?: "No active sync"))
                        .line(Component.literal("Client receiving tiles is not proof they are visible on Xaero World Map."))
                        .line(Component.literal("Downloaded enemy terrain cannot yet be revoked.")
                            .withStyle(ChatFormatting.GOLD))
                        .build()
                )
                .build()
        )
        if (status?.admin == true) {
            builder = builder.category(
                ConfigCategory.createBuilder()
                    .name(t("categories.admin.name"))
                    .option(
                        LabelOption.createBuilder()
                            .line(t("categories.admin.warning.headline").copy()
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                            .line(t("categories.admin.warning.message").copy()
                                .withStyle(ChatFormatting.RED))
                            .build()
                    )
                    .simpleOption("admin", adminDefaults::allowTeam, adminDraft::allowTeam,
                        TickBoxControllerBuilder::create)
                    .simpleOption("admin", adminDefaults::allowGlobal, adminDraft::allowGlobal,
                        TickBoxControllerBuilder::create)
                    .option(
                        Option.createBuilder<Boolean>()
                            .name(t("categories.admin.options.globalRiskAcknowledged.name")
                                .copy().withStyle(ChatFormatting.RED))
                            .description(OptionDescription.of(
                                t("categories.admin.options.globalRiskAcknowledged.description")))
                            .binding(false, { adminGlobalAcknowledged },
                                { adminGlobalAcknowledged = it })
                            .controller(TickBoxControllerBuilder::create)
                            .build()
                    )
                    .simpleOption("admin", adminDefaults::allowUploads, adminDraft::allowUploads,
                        TickBoxControllerBuilder::create)
                    .build()
            )
        }

        val worldId = XMMPClient.getWorldId()
        if (worldId != null) {
            val worldConfig = i.worlds.getOrPut(worldId) { XMMPConfig.WorldConfig() }
            val worldDefaults = XMMPConfig.WorldConfig()

            builder = builder.category(
                ConfigCategory.createBuilder()
                    .name(t("categories.world.name"))
                    .simpleOption(
                        "world",
                        worldDefaults::serverAddress,
                        worldConfig::serverAddress,
                        StringControllerBuilder::create
                    )
                    .build()
            )
        }

        return builder.build().generateScreen(parent)
    }
}
