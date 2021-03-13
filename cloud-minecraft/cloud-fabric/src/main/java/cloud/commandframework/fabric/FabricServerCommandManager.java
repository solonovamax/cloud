//
// MIT License
//
// Copyright (c) 2021 Alexander Söderberg & Contributors
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//

package cloud.commandframework.fabric;

import cloud.commandframework.CommandTree;
import cloud.commandframework.arguments.parser.ParserParameters;
import cloud.commandframework.execution.AsynchronousCommandExecutionCoordinator;
import cloud.commandframework.execution.CommandExecutionCoordinator;
import cloud.commandframework.fabric.annotations.specifier.Center;
import cloud.commandframework.fabric.argument.FabricArgumentParsers;
import cloud.commandframework.fabric.argument.FabricArgumentParsers.FabricParserParameters;
import cloud.commandframework.fabric.data.Coordinates;
import cloud.commandframework.fabric.data.Message;
import cloud.commandframework.fabric.data.MultipleEntitySelector;
import cloud.commandframework.fabric.data.MultiplePlayerSelector;
import cloud.commandframework.fabric.data.SingleEntitySelector;
import cloud.commandframework.fabric.data.SinglePlayerSelector;
import cloud.commandframework.meta.CommandMeta;
import io.leangen.geantyref.TypeToken;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.function.Function;

/**
 * A command manager for registering server-side commands.
 *
 * <p>All commands should be registered within mod initializers. Any registrations occurring after the first call to
 * {@link CommandRegistrationCallback} will be considered <em>unsafe</em>, and will only be permitted when the unsafe
 * registration manager option is enabled.</p>
 *
 * @param <C> the command sender type
 * @since 1.5.0
 */
public final class FabricServerCommandManager<C> extends FabricCommandManager<C, ServerCommandSource> {

    /**
     * A meta attribute specifying which environments a command should be registered in.
     *
     * <p>The default value is {@link CommandManager.RegistrationEnvironment#ALL}.</p>
     *
     * @since 1.5.0
     */
    public static final CommandMeta.Key<CommandManager.RegistrationEnvironment> META_REGISTRATION_ENVIRONMENT = CommandMeta.Key.of(
            CommandManager.RegistrationEnvironment.class,
            "cloud:registration-environment"
    );

    /**
     * Create a command manager using native source types.
     *
     * @param execCoordinator Execution coordinator instance.
     * @return a new command manager
     * @see #FabricServerCommandManager(Function, Function, Function) for a more thorough explanation
     * @since 1.5.0
     */
    public static @NonNull FabricServerCommandManager<@NonNull ServerCommandSource> createNative(
            final @NonNull Function<@NonNull CommandTree<@NonNull ServerCommandSource>,
                    @NonNull CommandExecutionCoordinator<@NonNull ServerCommandSource>> execCoordinator
    ) {
        return new FabricServerCommandManager<>(execCoordinator, Function.identity(), Function.identity());
    }

    /**
     * Create a new command manager instance.
     *
     * @param commandExecutionCoordinator  Execution coordinator instance. The coordinator is in charge of executing incoming
     *                                     commands. Some considerations must be made when picking a suitable execution coordinator
     *                                     for your platform. For example, an entirely asynchronous coordinator is not suitable
     *                                     when the parsers used in that particular platform are not thread safe. If you have
     *                                     commands that perform blocking operations, however, it might not be a good idea to
     *                                     use a synchronous execution coordinator. In most cases you will want to pick between
     *                                     {@link CommandExecutionCoordinator#simpleCoordinator()} and
     *                                     {@link AsynchronousCommandExecutionCoordinator}
     * @param commandSourceMapper          Function that maps {@link ServerCommandSource} to the command sender type
     * @param backwardsCommandSourceMapper Function that maps the command sender type to {@link ServerCommandSource}
     * @since 1.5.0
     */
    public FabricServerCommandManager(
            final @NonNull Function<@NonNull CommandTree<@NonNull C>,
                    @NonNull CommandExecutionCoordinator<@NonNull C>> commandExecutionCoordinator,
            final @NonNull Function<@NonNull ServerCommandSource, @NonNull C> commandSourceMapper,
            final @NonNull Function<@NonNull C, @NonNull ServerCommandSource> backwardsCommandSourceMapper
    ) {
        super(
                commandExecutionCoordinator,
                commandSourceMapper,
                backwardsCommandSourceMapper,
                new FabricCommandRegistrationHandler.Server<>(),
                () -> new ServerCommandSource(
                        CommandOutput.DUMMY,
                        Vec3d.ZERO,
                        Vec2f.ZERO,
                        null,
                        4,
                        "",
                        LiteralText.EMPTY,
                        null,
                        null
                )
        );

        this.registerParsers();
    }

    private void registerParsers() {
        this.getParserRegistry().registerParserSupplier(TypeToken.get(Message.class), params -> FabricArgumentParsers.message());

        // Location arguments
        this.getParserRegistry().registerAnnotationMapper(
                Center.class,
                (annotation, type) -> ParserParameters.single(FabricParserParameters.CENTER_INTEGERS, true)
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(Coordinates.class),
                params -> FabricArgumentParsers.vec3(params.get(
                        FabricParserParameters.CENTER_INTEGERS,
                        false
                ))
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(Coordinates.CoordinatesXZ.class),
                params -> FabricArgumentParsers.vec2(params.get(
                        FabricParserParameters.CENTER_INTEGERS,
                        false
                ))
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(Coordinates.BlockCoordinates.class),
                params -> FabricArgumentParsers.blockPos()
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(Coordinates.ColumnCoordinates.class),
                params -> FabricArgumentParsers.columnPos()
        );

        // Entity selectors
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(SinglePlayerSelector.class),
                params -> FabricArgumentParsers.singlePlayerSelector()
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(MultiplePlayerSelector.class),
                params -> FabricArgumentParsers.multiplePlayerSelector()
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(SingleEntitySelector.class),
                params -> FabricArgumentParsers.singleEntitySelector()
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(MultipleEntitySelector.class),
                params -> FabricArgumentParsers.multipleEntitySelector()
        );
    }

    /**
     * Check if a sender has a certain permission.
     *
     * <p>The current implementation checks op level, pending a full Fabric permissions api.</p>
     *
     * @param sender     Command sender
     * @param permission Permission node
     * @return whether the sender has the specified permission
     * @since 1.5.0
     */
    @Override
    public boolean hasPermission(final @NonNull C sender, final @NonNull String permission) {
        final ServerCommandSource source = this.getBackwardsCommandSourceMapper().apply(sender);
        return Permissions.check(source, permission, source.getMinecraftServer().getOpPermissionLevel());
    }

}
