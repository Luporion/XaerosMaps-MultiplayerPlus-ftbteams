package dev.alinco8.xmmp.config

import dev.alinco8.xmmp.client.XMMPClient
import dev.isxander.yacl3.api.ConfigCategory
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
        val statusLabel = when {
            status == null -> "Not connected to an XMMP server"
            status.partyScope.isEmpty() -> "No FTB party / publishing: ${status.activePublishScope.ifEmpty { "disabled" }}"
            else -> "FTB party: ${status.partyScope.removePrefix("team-")} / publishing: ${status.activePublishScope.ifEmpty { "disabled" }}"
        }

        var builder = YetAnotherConfigLib.createBuilder()
            .title(t("title"))
            .save {
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
                    Option.createBuilder<String>()
                        .name(t("categories.sharing.options.terrainSharingMode.name"))
                        .description(OptionDescription.of(t("categories.sharing.options.terrainSharingMode.description")))
                        .bind(d::terrainSharingMode, i::terrainSharingMode)
                        .controller { option -> CyclingListControllerBuilder.create(option)
                            .values(listOf("PRIVATE", "TEAM", "GLOBAL"))
                            .valueFormatter { Component.literal(it) } }
                        .build()
                )
                .simpleOption("sharing", d::receiveTeamTerrain, i::receiveTeamTerrain,
                    TickBoxControllerBuilder::create)
                .simpleOption("sharing", d::receiveGlobalTerrain, i::receiveGlobalTerrain,
                    TickBoxControllerBuilder::create)
                .build()
        )
        // Server-confirmed, disabled status control. Never infer team from client config.
        builder = builder.category(
            ConfigCategory.createBuilder()
                .name(Component.literal("XMMP Status"))
                .option(
                    Option.createBuilder<Boolean>()
                        .name(Component.literal(statusLabel))
                        .description(OptionDescription.of(Component.literal(
                            "Team download: ${status?.receiveTeam == true}, global download: ${status?.receiveGlobal == true}. " +
                            "${XMMPClient.session?.syncDiagnostics() ?: "No active sync"}. " +
                            "New permissions do not erase already cached Xaero map tiles."
                        )))
                        .binding(false, { false }, {})
                        .controller(TickBoxControllerBuilder::create)
                        .available(false)
                        .build()
                )
                .build()
        )
        if (status?.admin == true) {
            builder = builder.category(
                ConfigCategory.createBuilder()
                    .name(t("categories.admin.name"))
                    .simpleOption("admin", adminDefaults::allowTeam, adminDraft::allowTeam,
                        TickBoxControllerBuilder::create)
                    .simpleOption("admin", adminDefaults::allowGlobal, adminDraft::allowGlobal,
                        TickBoxControllerBuilder::create)
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
