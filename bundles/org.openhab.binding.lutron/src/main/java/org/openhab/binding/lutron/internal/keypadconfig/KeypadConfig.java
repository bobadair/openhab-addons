/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.lutron.internal.keypadconfig;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.openhab.binding.lutron.internal.KeypadComponent;
import org.openhab.binding.lutron.internal.discovery.project.ComponentType;

/**
 * Abstract base class for keypad configuration definition classes
 *
 * @author Bob Adair - Initial contribution
 */
public abstract class KeypadConfig {

    protected HashMap<String, List<KeypadComponent>> modelData;

    public KeypadConfig() {
        super();
    }

    public abstract boolean isCCI(int id);

    public abstract boolean isButton(int id);

    public abstract boolean isLed(int id);

    public List<KeypadComponent> getComponents(String model) {
        return modelData.get(model);
    }

    public List<KeypadComponent> getComponents(String model, ComponentType type) {
        List<KeypadComponent> filteredList = new LinkedList<>();
        for (KeypadComponent i : modelData.get(model)) {
            if (type == null || i.type() == type) {
                filteredList.add(i);
            }
        }
        return filteredList;
    }

    public List<Integer> getComponentIds(String model) {
        return getComponentIds(model, null);
    }

    public List<Integer> getComponentIds(String model, ComponentType type) {
        List<Integer> idList = new LinkedList<>();
        for (KeypadComponent i : modelData.get(model)) {
            if (type == null || i.type() == type) {
                idList.add(i.id());
            }
        }
        return idList;
    }

}
