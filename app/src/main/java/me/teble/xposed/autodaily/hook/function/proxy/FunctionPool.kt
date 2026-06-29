package me.teble.xposed.autodaily.hook.function.proxy

import android.content.Context
import me.teble.xposed.autodaily.hook.base.hostContext
import me.teble.xposed.autodaily.hook.function.BaseSendMessage
import me.teble.xposed.autodaily.hook.function.base.BaseFunction
import me.teble.xposed.autodaily.hook.function.impl.FavoriteManager
import me.teble.xposed.autodaily.hook.function.impl.FriendsManager
import me.teble.xposed.autodaily.hook.function.impl.GroupSignInManager
import me.teble.xposed.autodaily.hook.function.impl.GuildManager
import me.teble.xposed.autodaily.hook.function.impl.MiniLoginManager
import me.teble.xposed.autodaily.hook.function.impl.MiniProfileManager
import me.teble.xposed.autodaily.hook.function.impl.NtSendMessageManager
import me.teble.xposed.autodaily.hook.function.impl.PublicAccountManager
import me.teble.xposed.autodaily.hook.function.impl.QZIntimateSpaceManager
import me.teble.xposed.autodaily.hook.function.impl.SendMessageManager
import me.teble.xposed.autodaily.hook.function.impl.TicketManager
import me.teble.xposed.autodaily.hook.function.impl.TroopManager
import me.teble.xposed.autodaily.hook.function.impl.YunDongStepsManager
import me.teble.xposed.autodaily.hook.utils.QApplicationUtil
import net.bytebuddy.ByteBuddy
import net.bytebuddy.android.AndroidClassLoadingStrategy
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.matcher.ElementMatchers
import java.io.File
import java.lang.reflect.Modifier


object FunctionPool {

    /**
     * 修复 Android 10+ "Writable dex file is not allowed" 问题。
     *
     * ByteBuddy 的 FILE_PROCESSOR 用 java.nio.file.Files.setPosixFilePermissions 设只读，
     * 但某些 Android 版本上该 API 不可用，反射失败后回退到 Disabled（不设权限），
     * 导致 DexFile 构造时 canWrite()=true → SecurityException。
     *
     * 修复：子类化 Wrapping，在 doLoad 里先 jar.setReadOnly() 再调 super。
     * java.io.File.setReadOnly() 所有 Android 版本都支持。
     */
    private class ReadOnlyWrapping(privateDirectory: File) :
        AndroidClassLoadingStrategy.Wrapping(privateDirectory) {

        override fun doLoad(
            classLoader: ClassLoader?,
            typeDescriptions: MutableSet<TypeDescription>,
            jar: File
        ): MutableMap<TypeDescription, Class<*>> {
            // 确保 dex jar 文件在 DexClassLoader 加载前是只读的
            if (!jar.setReadOnly()) {
                // setReadOnly 失败时尝试 POSIX 权限（兼容性更好）
                try {
                    val path = File::class.java.getMethod("toPath").invoke(jar)
                    val perms = java.util.Collections.singleton(
                        Class.forName("java.nio.file.attribute.PosixFilePermission")
                            .getMethod("valueOf", String::class.java)
                            .invoke(null, "OWNER_READ")
                    )
                    Class.forName("java.nio.file.Files")
                        .getMethod("setPosixFilePermissions", Class.forName("java.nio.file.Path"), java.util.Set::class.java)
                        .invoke(null, path, perms)
                } catch (_: Throwable) {
                    // 两种方式都失败，继续尝试加载，可能还是会崩
                }
            }
            return super.doLoad(classLoader, typeDescriptions, jar)
        }
    }

    private val functionArray: Array<Class<out BaseFunction>> = arrayOf(
        FavoriteManager::class.java,
        TicketManager::class.java,
        FriendsManager::class.java,
        TroopManager::class.java,
        GuildManager::class.java,
        MiniLoginManager::class.java,
        NtSendMessageManager::class.java,
        SendMessageManager::class.java,
        GroupSignInManager::class.java,
        MiniProfileManager::class.java,
        PublicAccountManager::class.java,
        YunDongStepsManager::class.java,
        QZIntimateSpaceManager::class.java,
    )

    private val functionMap = HashMap<Class<out BaseFunction>, BaseFunction>().let {
        val strategy = ReadOnlyWrapping(
            hostContext.getDir(
                "generated",
                Context.MODE_PRIVATE
            )
        )
        for (cls in functionArray) {
            if (Modifier.toString(cls.modifiers).contains("final")) {
                throw RuntimeException("修饰符错误")
            }
            it[cls] = ByteBuddy()
                .subclass(cls)
                .method(
                    ElementMatchers.not(ElementMatchers.isDeclaredBy(Any::class.java))
                        .and(ElementMatchers.not(ElementMatchers.isOverriddenFrom(BaseFunction::class.java)))
                )
                .intercept(MethodDelegation.to(FunctionProxy()))
                .make()
                .load(cls.classLoader, strategy)
                .loaded.newInstance()
        }
        it
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : BaseFunction> getFunction(functionClass: Class<T>) = functionMap[functionClass] as T

    val favoriteManager by lazy { getFunction(FavoriteManager::class.java) }

    val ticketManager by lazy { getFunction(TicketManager::class.java) }

    val friendsManager by lazy { getFunction(FriendsManager::class.java) }

    val troopManager by lazy { getFunction(TroopManager::class.java) }

    val guildManager by lazy { getFunction(GuildManager::class.java) }

    val miniLoginManager by lazy { getFunction(MiniLoginManager::class.java) }

    val sendMessageManager: BaseSendMessage by lazy {
        if (QApplicationUtil.isNtQQ()) {
            getFunction(NtSendMessageManager::class.java)
        } else {
            getFunction(SendMessageManager::class.java)
        }
    }

    val groupSignInManager by lazy { getFunction(GroupSignInManager::class.java) }

    val miniProfileManager by lazy { getFunction(MiniProfileManager::class.java) }

    val publicAccountManager by lazy { getFunction(PublicAccountManager::class.java) }

    val yunDongStepsManager by lazy { getFunction(YunDongStepsManager::class.java) }

    val qzIntimateSpaceManager by lazy { getFunction(QZIntimateSpaceManager::class.java) }
}
