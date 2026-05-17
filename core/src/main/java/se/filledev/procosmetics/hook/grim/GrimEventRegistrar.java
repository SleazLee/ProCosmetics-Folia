/*
 * This file is part of ProCosmetics - https://github.com/FilleDev/ProCosmetics
 * Copyright (C) 2025-2026 FilleDev and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package se.filledev.procosmetics.hook.grim;

import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.GrimAbstractAPI;
import ac.grim.grimac.api.plugin.GrimPlugin;
import se.filledev.procosmetics.ProCosmeticsPlugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;

final class GrimEventRegistrar {

    private static final int FINAL_CANCELLATION_PRIORITY = Integer.MAX_VALUE;
    private static final String GRIM_EVENT_LISTENER_CLASS = "ac.grim.grimac.api.event.GrimEventListener";
    private static final String[] CHECK_EVENT_CLASSES = {
            "ac.grim.grimac.api.event.events.GrimCheckEvent",
            "ac.grim.grimac.api.event.events.GrimVerboseCheckEvent",
            "ac.grim.grimac.api.event.events.FlagEvent",
            "ac.grim.grimac.api.event.events.CompletePredictionEvent"
    };

    private GrimEventRegistrar() {
    }

    /**
     * Registers Grim check listeners without linking to one exact EventBus revision.
     *
     * <p>Different GrimAC builds expose different EventBus helpers. Some provide
     * channel lookup methods, while the Folia build reported by users only keeps
     * the generic {@code subscribe(...)} API. Reflection avoids a hard
     * {@code NoSuchMethodError} at startup and lets ProCosmetics subscribe to the
     * check event classes that are actually present in the installed Grim jar.</p>
     *
     * @param plugin the ProCosmetics plugin instance used as the Grim listener owner
     * @param exemptionManager the manager that decides whether a player is currently exempt
     */
    static void register(ProCosmeticsPlugin plugin, GrimExemptionManager exemptionManager) {
        GrimAbstractAPI api = GrimAPIProvider.get();
        GrimPlugin grimPlugin = api.getGrimPlugin(plugin);
        Object eventBus = api.getEventBus();
        Object listener = createListener(exemptionManager);
        int registrations = 0;

        for (String eventClassName : CHECK_EVENT_CLASSES) {
            Class<?> eventClass = findClass(eventClassName);

            if (eventClass != null && subscribe(eventBus, grimPlugin, eventClass, listener)) {
                registrations++;
            }
        }
        if (registrations == 0) {
            throw new IllegalStateException("No compatible GrimAC check event subscription method was found.");
        }
    }

    /**
     * Removes all Grim listeners registered for ProCosmetics.
     *
     * <p>Grim tracks event subscriptions by plugin context. Unregistering through
     * the same resolved context prevents stale asynchronous handlers from reading
     * ProCosmetics state after the plugin has disabled or reloaded.</p>
     *
     * @param plugin the ProCosmetics plugin instance used during registration
     */
    static void unregister(ProCosmeticsPlugin plugin) {
        GrimAbstractAPI api = GrimAPIProvider.get();
        Object eventBus = api.getEventBus();
        Object grimPlugin = api.getGrimPlugin(plugin);

        for (Method method : eventBus.getClass().getMethods()) {
            if (!"unregisterAllListeners".equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> parameterType = method.getParameterTypes()[0];

            if (parameterType.isInstance(grimPlugin) || parameterType == Object.class) {
                invoke(method, eventBus, grimPlugin);
                return;
            }
        }
    }

    /**
     * Builds a listener using Grim's runtime listener interface.
     *
     * <p>The proxy keeps this class from linking directly to a particular
     * {@code GrimEventListener} bytecode shape. Grim invokes the listener on its
     * own checking thread, so the handler only reads UUID/expiry data from
     * {@link GrimExemptionManager} and never touches Bukkit player state.</p>
     *
     * @param exemptionManager the active exemption store
     * @return a proxy implementing Grim's listener interface
     */
    private static Object createListener(GrimExemptionManager exemptionManager) {
        Class<?> listenerType = requireClass(GRIM_EVENT_LISTENER_CLASS);
        InvocationHandler handler = (proxy, method, args) -> {
            if ("handle".equals(method.getName()) && args != null && args.length == 1) {
                cancelIfExempt(args[0], exemptionManager);
                return null;
            }
            if ("toString".equals(method.getName())) {
                return "ProCosmetics GrimAC exemption listener";
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName()) && args != null && args.length == 1) {
                return proxy == args[0];
            }
            return null;
        };

        return Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[]{listenerType}, handler);
    }

    /**
     * Subscribes one event class through whichever generic EventBus signature Grim exposes.
     *
     * <p>Modern Grim versions include overloads with plugin context, priority,
     * cancelled-state delivery, and an optional bridge class. Older builds may
     * expose fewer parameters. Trying the richest compatible overload first keeps
     * ProCosmetics at final priority when possible while still supporting older
     * Grim jars.</p>
     *
     * @param eventBus Grim's runtime event bus
     * @param context the Grim plugin context for ProCosmetics
     * @param eventClass the check event class to subscribe to
     * @param listener the runtime listener proxy
     * @return {@code true} if a compatible overload accepted the subscription
     */
    private static boolean subscribe(Object eventBus, Object context, Class<?> eventClass, Object listener) {
        Class<?> listenerType = requireClass(GRIM_EVENT_LISTENER_CLASS);

        return Arrays.stream(eventBus.getClass().getMethods())
                .filter(method -> "subscribe".equals(method.getName()))
                .filter(method -> isCompatibleSubscribeMethod(method, context, listenerType))
                .sorted(Comparator.comparingInt(Method::getParameterCount).reversed())
                .anyMatch(method -> trySubscribe(method, eventBus, context, eventClass, listener));
    }

    private static boolean isCompatibleSubscribeMethod(Method method, Object context, Class<?> listenerType) {
        Class<?>[] parameterTypes = method.getParameterTypes();

        if (parameterTypes.length < 3 || parameterTypes.length > 6) {
            return false;
        }
        return parameterTypes[0].isInstance(context)
                && parameterTypes[1] == Class.class
                && parameterTypes[2].isAssignableFrom(listenerType);
    }

    private static boolean trySubscribe(Method method, Object eventBus, Object context, Class<?> eventClass, Object listener) {
        try {
            invoke(method, eventBus, buildSubscribeArguments(method, context, eventClass, listener));
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static Object[] buildSubscribeArguments(Method method, Object context, Class<?> eventClass, Object listener) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];

        arguments[0] = context;
        arguments[1] = eventClass;
        arguments[2] = listener;

        if (parameterTypes.length >= 4) {
            arguments[3] = FINAL_CANCELLATION_PRIORITY;
        }
        if (parameterTypes.length >= 5) {
            arguments[4] = true;
        }
        if (parameterTypes.length >= 6) {
            arguments[5] = listener.getClass();
        }
        return arguments;
    }

    private static void cancelIfExempt(Object event, GrimExemptionManager exemptionManager) {
        UUID uuid = getEventUserUuid(event);
        Method setCancelled = findMethod(event.getClass(), "setCancelled", boolean.class);

        if (uuid != null && setCancelled != null && exemptionManager.isExempt(uuid) && isCancellable(event)) {
            invoke(setCancelled, event, true);
        }
    }

    private static UUID getEventUserUuid(Object event) {
        Method getUser = findMethod(event.getClass(), "getUser");

        if (getUser == null) {
            return null;
        }
        Object user = invoke(getUser, event);

        if (user == null) {
            return null;
        }
        Method getUniqueId = findMethod(user.getClass(), "getUniqueId");

        if (getUniqueId == null) {
            return null;
        }
        Object uuid = invoke(getUniqueId, user);
        return uuid instanceof UUID ? (UUID) uuid : null;
    }

    private static boolean isCancellable(Object event) {
        Method isCancellable = findMethod(event.getClass(), "isCancellable");

        if (isCancellable == null) {
            return findMethod(event.getClass(), "setCancelled", boolean.class) != null;
        }
        Object result = invoke(isCancellable, event);
        return Boolean.TRUE.equals(result);
    }

    private static Class<?> findClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static Class<?> requireClass(String className) {
        Class<?> clazz = findClass(className);

        if (clazz == null) {
            throw new IllegalStateException("Required GrimAC class is missing: " + className);
        }
        return clazz;
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method findRequiredMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        Method method = findMethod(owner, name, parameterTypes);

        if (method == null) {
            throw new IllegalStateException("Required GrimAC method is missing: " + owner.getName() + "#" + name);
        }
        return method;
    }

    private static Object invoke(Method method, Object target, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to call GrimAC API method " + method, exception);
        }
    }
}
