/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.livisismarthome.internal.handler;

import static org.openhab.binding.livisismarthome.internal.LivisiBindingConstants.*;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.livisismarthome.internal.client.api.entity.action.ShutterActionType;
import org.openhab.binding.livisismarthome.internal.client.api.entity.capability.CapabilityDTO;
import org.openhab.binding.livisismarthome.internal.client.api.entity.capability.CapabilityStateDTO;
import org.openhab.binding.livisismarthome.internal.client.api.entity.device.DeviceDTO;
import org.openhab.binding.livisismarthome.internal.client.api.entity.event.EventDTO;
import org.openhab.binding.livisismarthome.internal.listener.DeviceStatusListener;
import org.openhab.core.library.types.*;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.CommonTriggerEvents;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link LivisiDeviceHandler} is responsible for handling the {@link DeviceDTO}s and their commands, which are
 * sent to one of the channels.
 *
 * @author Oliver Kuhl - Initial contribution
 * @author Sven Strohschein - Renamed from Innogy to Livisi
 */
@NonNullByDefault
public class LivisiDeviceHandler extends BaseThingHandler implements DeviceStatusListener {

    private static final String DEBUG = "DEBUG";
    private static final String LONG_PRESS = "LongPress";
    private static final String SHORT_PRESS = "ShortPress";

    private final Logger logger = LoggerFactory.getLogger(LivisiDeviceHandler.class);
    private final Object lock = new Object();

    private String deviceId = "";
    private @Nullable LivisiBridgeHandler bridgeHandler;

    /**
     * Constructs a new {@link LivisiDeviceHandler} for the given {@link Thing}.
     *
     * @param thing device thing
     */
    public LivisiDeviceHandler(final Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(final ChannelUID channelUID, final Command command) {
        logger.debug("handleCommand called for channel '{}' of type '{}' with command '{}'", channelUID,
                getThing().getThingTypeUID().getId(), command);

        final Optional<LivisiBridgeHandler> bridgeHandlerOptional = getBridgeHandler();
        if (bridgeHandlerOptional.isPresent()) {
            LivisiBridgeHandler bridgeHandler = bridgeHandlerOptional.get();
            if (ThingStatus.ONLINE.equals(bridgeHandler.getThing().getStatus())) {
                if (command instanceof RefreshType) {
                    final Optional<DeviceDTO> device = bridgeHandler.getDeviceById(deviceId);
                    device.ifPresent(this::onDeviceStateChanged);
                } else {
                    executeCommand(channelUID, command, bridgeHandler);
                }
            } else {
                logger.debug("Cannot handle command - bridge is not online. Command ignored.");
            }
        } else {
            logger.warn("BridgeHandler not found. Cannot handle command without bridge.");
        }
    }

    private void executeCommand(ChannelUID channelUID, Command command, LivisiBridgeHandler bridgeHandler) {
        if (CHANNEL_SWITCH.equals(channelUID.getId())) {
            commandSwitchDevice(command, bridgeHandler);
        } else if (CHANNEL_DIMMER.equals(channelUID.getId())) {
            commandSetDimLevel(command, bridgeHandler);
        } else if (CHANNEL_ROLLERSHUTTER.equals(channelUID.getId())) {
            commandRollerShutter(command, bridgeHandler);
        } else if (CHANNEL_SET_TEMPERATURE.equals(channelUID.getId())) {
            commandUpdatePointTemperature(command, bridgeHandler);
        } else if (CHANNEL_OPERATION_MODE.equals(channelUID.getId())) {
            commandSetOperationMode(command, bridgeHandler);
        } else if (CHANNEL_ALARM.equals(channelUID.getId())) {
            commandSwitchAlarm(command, bridgeHandler);
        } else {
            logger.debug("UNSUPPORTED channel {} for device {}.", channelUID.getId(), deviceId);
        }
    }

    private void commandSwitchDevice(Command command, LivisiBridgeHandler bridgeHandler) {
        // DEBUGGING HELPER
        // ----------------
        final Optional<DeviceDTO> device = bridgeHandler.getDeviceById(deviceId);
        if (device.isPresent() && DEBUG.equals(device.get().getConfig().getName())) {
            logger.debug("DEBUG SWITCH ACTIVATED!");
            if (OnOffType.ON.equals(command)) {
                bridgeHandler.onEvent(
                        "{\"sequenceNumber\": -1,\"type\": \"MessageCreated\",\"desc\": \"/desc/event/MessageCreated\",\"namespace\": \"core.RWE\",\"timestamp\": \"2019-07-07T18:41:47.2970000Z\",\"source\": \"/desc/device/SHC.RWE/1.0\",\"data\": {\"id\": \"6e5ce2290cd247208f95a5b53736958b\",\"type\": \"DeviceLowBattery\",\"read\": false,\"class\": \"Alert\",\"timestamp\": \"2019-07-07T18:41:47.232Z\",\"devices\": [\"/device/fe51785319854f36a621d0b4f8ea0e25\"],\"properties\": {\"deviceName\": \"Heizkörperthermostat\",\"serialNumber\": \"914110165056\",\"locationName\": \"Bad\"},\"namespace\": \"core.RWE\"}}");
            } else {
                bridgeHandler.onEvent(
                        "{\"sequenceNumber\": -1,\"type\": \"MessageDeleted\",\"desc\": \"/desc/event/MessageDeleted\",\"namespace\": \"core.RWE\",\"timestamp\": \"2019-07-07T19:15:39.2100000Z\",\"data\": { \"id\": \"6e5ce2290cd247208f95a5b53736958b\" }}");
            }
        } else {
            if (command instanceof OnOffType) {
                bridgeHandler.commandSwitchDevice(deviceId, OnOffType.ON.equals(command));
            }
        }
    }

    private void commandSetDimLevel(Command command, LivisiBridgeHandler bridgeHandler) {
        if (command instanceof DecimalType) {
            final DecimalType dimLevel = (DecimalType) command;
            bridgeHandler.commandSetDimmLevel(deviceId, dimLevel.intValue());
        } else if (command instanceof OnOffType) {
            if (OnOffType.ON.equals(command)) {
                bridgeHandler.commandSetDimmLevel(deviceId, 100);
            } else {
                bridgeHandler.commandSetDimmLevel(deviceId, 0);
            }
        }
    }

    private void commandRollerShutter(Command command, LivisiBridgeHandler bridgeHandler) {
        if (command instanceof DecimalType) {
            final DecimalType rollerShutterLevel = (DecimalType) command;
            bridgeHandler.commandSetRollerShutterLevel(deviceId,
                    invertRollerShutterValueIfConfigured(rollerShutterLevel.intValue()));
        } else if (command instanceof OnOffType) {
            if (OnOffType.ON.equals(command)) {
                bridgeHandler.commandSetRollerShutterStop(deviceId, ShutterActionType.DOWN);
            } else {
                bridgeHandler.commandSetRollerShutterStop(deviceId, ShutterActionType.UP);
            }
        } else if (command instanceof UpDownType) {
            if (UpDownType.DOWN.equals(command)) {
                bridgeHandler.commandSetRollerShutterStop(deviceId, ShutterActionType.DOWN);
            } else {
                bridgeHandler.commandSetRollerShutterStop(deviceId, ShutterActionType.UP);
            }
        } else if (command instanceof StopMoveType) {
            if (StopMoveType.STOP.equals(command)) {
                bridgeHandler.commandSetRollerShutterStop(deviceId, ShutterActionType.STOP);
            }
        }
    }

    private void commandUpdatePointTemperature(Command command, LivisiBridgeHandler bridgeHandler) {
        if (command instanceof QuantityType) {
            final QuantityType pointTemperature = (QuantityType) command;
            if (pointTemperature.doubleValue() >= 6 && pointTemperature.doubleValue() <= 30) {
                bridgeHandler.commandUpdatePointTemperature(deviceId, pointTemperature.doubleValue());
            }
        }
    }

    private void commandSetOperationMode(Command command, LivisiBridgeHandler bridgeHandler) {
        if (command instanceof StringType) {
            final String autoModeCommand = command.toString();

            if (CapabilityStateDTO.STATE_VALUE_OPERATION_MODE_AUTO.equals(autoModeCommand)) {
                bridgeHandler.commandSetOperationMode(deviceId, true);
            } else if (CapabilityStateDTO.STATE_VALUE_OPERATION_MODE_MANUAL.equals(autoModeCommand)) {
                bridgeHandler.commandSetOperationMode(deviceId, false);
            } else {
                logger.warn("Could not set operationMode. Invalid value '{}'! Only '{}' or '{}' allowed.",
                        autoModeCommand, CapabilityStateDTO.STATE_VALUE_OPERATION_MODE_AUTO,
                        CapabilityStateDTO.STATE_VALUE_OPERATION_MODE_MANUAL);
            }
        }
    }

    private void commandSwitchAlarm(Command command, LivisiBridgeHandler bridgeHandler) {
        if (command instanceof OnOffType) {
            bridgeHandler.commandSwitchAlarm(deviceId, OnOffType.ON.equals(command));
        }
    }

    @Override
    public void initialize() {
        logger.debug("Initializing LIVISI SmartHome device handler.");
        initializeThing(getBridgeStatus());
    }

    @Override
    public void dispose() {
        if (bridgeHandler != null) {
            bridgeHandler.unregisterDeviceStatusListener(deviceId);
        }
    }

    @Override
    public void bridgeStatusChanged(final ThingStatusInfo bridgeStatusInfo) {
        logger.debug("bridgeStatusChanged {}", bridgeStatusInfo);
        initializeThing(bridgeStatusInfo.getStatus());
    }

    /**
     * Initializes the {@link Thing} corresponding to the given status of the bridge.
     * 
     * @param bridgeStatus thing status of the bridge
     */
    private void initializeThing(@Nullable final ThingStatus bridgeStatus) {
        logger.debug("initializeThing thing {} bridge status {}", getThing().getUID(), bridgeStatus);
        final String configDeviceId = (String) getConfig().get(PROPERTY_ID);
        if (configDeviceId != null) {
            deviceId = configDeviceId;

            Optional<LivisiBridgeHandler> bridgeHandler = registerAtBridgeHandler();
            if (bridgeHandler.isPresent()) {
                if (ThingStatus.ONLINE == bridgeStatus) {
                    initializeProperties();

                    Optional<DeviceDTO> deviceOptional = getDevice();
                    if (deviceOptional.isPresent()) {
                        DeviceDTO device = deviceOptional.get();
                        if (device.isReachable() != null && !device.isReachable()) {
                            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                                    "Device not reachable.");
                        } else {
                            updateStatus(ThingStatus.ONLINE);
                        }
                    } else {
                        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.GONE,
                                "Device not found in LIVISI SmartHome config. Was it removed?");
                    }
                } else {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
                }
            } else {
                updateStatus(ThingStatus.OFFLINE);
            }
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "device id unknown");
        }
    }

    /**
     * Initializes all properties of the {@link DeviceDTO}, like vendor, serialnumber etc.
     */
    private void initializeProperties() {
        synchronized (this.lock) {
            final Optional<DeviceDTO> deviceOptional = getDevice();
            if (deviceOptional.isPresent()) {
                DeviceDTO device = deviceOptional.get();

                final Map<String, String> properties = editProperties();
                properties.put(PROPERTY_ID, device.getId());
                properties.put(PROPERTY_PROTOCOL_ID, device.getConfig().getProtocolId());
                if (device.hasSerialNumber()) {
                    properties.put(Thing.PROPERTY_SERIAL_NUMBER, device.getSerialnumber());
                }
                properties.put(Thing.PROPERTY_VENDOR, device.getManufacturer());
                properties.put(PROPERTY_VERSION, device.getVersion());
                if (device.hasLocation()) {
                    properties.put(PROPERTY_LOCATION, device.getLocation().getName());
                }
                if (device.isBatteryPowered()) {
                    properties.put(PROPERTY_BATTERY_POWERED, "yes");
                } else {
                    properties.put(PROPERTY_BATTERY_POWERED, "no");
                }
                if (device.isController()) {
                    properties.put(PROPERTY_DEVICE_TYPE, "Controller");
                } else if (device.isVirtualDevice()) {
                    properties.put(PROPERTY_DEVICE_TYPE, "Virtual");
                } else if (device.isRadioDevice()) {
                    properties.put(PROPERTY_DEVICE_TYPE, "Radio");
                }

                // Thermostat
                if (DEVICE_RST.equals(device.getType()) || DEVICE_RST2.equals(device.getType())
                        || DEVICE_WRT.equals(device.getType())) {
                    properties.put(PROPERTY_DISPLAY_CURRENT_TEMPERATURE,
                            device.getConfig().getDisplayCurrentTemperature());
                }

                // Meter
                if (DEVICE_ANALOG_METER.equals(device.getType()) || DEVICE_GENERATION_METER.equals(device.getType())
                        || DEVICE_SMART_METER.equals(device.getType())
                        || DEVICE_TWO_WAY_METER.equals(device.getType())) {
                    properties.put(PROPERTY_METER_ID, device.getConfig().getMeterId());
                    properties.put(PROPERTY_METER_FIRMWARE_VERSION, device.getConfig().getMeterFirmwareVersion());
                }

                if (device.getConfig().getTimeOfAcceptance() != null) {
                    properties.put(PROPERTY_TIME_OF_ACCEPTANCE, device.getConfig().getTimeOfAcceptance()
                            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)));
                }
                if (device.getConfig().getTimeOfDiscovery() != null) {
                    properties.put(PROPERTY_TIME_OF_DISCOVERY, device.getConfig().getTimeOfDiscovery()
                            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)));
                }

                updateProperties(properties);

                onDeviceStateChanged(device);
            } else {
                logger.warn("initializeProperties: The device with id {} isn't found", deviceId);
            }
        }
    }

    @Override
    public void onDeviceStateChanged(final DeviceDTO device) {
        synchronized (this.lock) {
            updateChannels(device);
        }
    }

    @Override
    public void onDeviceStateChanged(final DeviceDTO device, final EventDTO event) {
        synchronized (this.lock) {
            if (event.isLinkedtoCapability()) {
                final String linkedCapabilityId = event.getSourceId();

                CapabilityDTO capability = device.getCapabilityMap().get(linkedCapabilityId);
                logger.trace("Loaded Capability {}, {} with id {}, device {} from device id {}",
                        capability.getType(), capability.getName(), capability.getId(), capability.getDeviceLink(),
                        device.getId());

                if (capability.hasState()) {
                    boolean deviceChanged = updateDevice(event, capability);
                    if (deviceChanged) {
                        updateChannels(device);
                    }
                } else {
                    logger.debug("Capability {} has no state (yet?) - refreshing device.", capability.getName());

                    Optional<DeviceDTO> deviceOptional = refreshDevice(linkedCapabilityId);
                    deviceOptional.ifPresent(this::updateChannels);
                }
            } else if (event.isLinkedtoDevice()) {
                if (device.hasDeviceState()) {
                    updateChannels(device);
                } else {
                    logger.debug("Device {}/{} has no state.", device.getConfig().getName(), device.getId());
                }
            }
        }
    }

    private Optional<DeviceDTO> refreshDevice(String linkedCapabilityId) {
        Optional<LivisiBridgeHandler> bridgeHandler = registerAtBridgeHandler();
        Optional<DeviceDTO> deviceOptional = bridgeHandler.flatMap(bh -> bh.refreshDevice(deviceId));
        if (deviceOptional.isPresent()) {
            DeviceDTO device = deviceOptional.get();
            CapabilityDTO capability = device.getCapabilityMap().get(linkedCapabilityId);
            if (capability.hasState()) {
                return Optional.of(device);
            }
        }
        return Optional.empty();
    }

    private boolean updateDevice(EventDTO event, CapabilityDTO capability) {
        CapabilityStateDTO capabilityState = capability.getCapabilityState();

        // VariableActuator
        if (capability.isTypeVariableActuator()) {
            capabilityState.setVariableActuatorState(event.getProperties().getValue());

            // SwitchActuator
        } else if (capability.isTypeSwitchActuator()) {
            capabilityState.setSwitchActuatorState(event.getProperties().getOnState());

            // DimmerActuator
        } else if (capability.isTypeDimmerActuator()) {
            capabilityState.setDimmerActuatorState(event.getProperties().getDimLevel());

            // RollerShutterActuator
        } else if (capability.isTypeRollerShutterActuator()) {
            capabilityState.setRollerShutterActuatorState(event.getProperties().getShutterLevel());

            // TemperatureSensor
        } else if (capability.isTypeTemperatureSensor()) {
            // when values are changed, they come with separate events
            // values should only updated when they are not null
            final Double temperature = event.getProperties().getTemperature();
            final Boolean frostWarning = event.getProperties().getFrostWarning();
            if (temperature != null) {
                capabilityState.setTemperatureSensorTemperatureState(temperature);
            }
            if (frostWarning != null) {
                capabilityState.setTemperatureSensorFrostWarningState(frostWarning);
            }

            // ThermostatActuator
        } else if (capability.isTypeThermostatActuator()) {
            // when values are changed, they come with separate events
            // values should only updated when they are not null

            final Double pointTemperature = event.getProperties().getPointTemperature();
            final String operationMode = event.getProperties().getOperationMode();
            final Boolean windowReductionActive = event.getProperties().getWindowReductionActive();

            if (pointTemperature != null) {
                capabilityState.setThermostatActuatorPointTemperatureState(pointTemperature);
            }
            if (operationMode != null) {
                capabilityState.setThermostatActuatorOperationModeState(operationMode);
            }
            if (windowReductionActive != null) {
                capabilityState.setThermostatActuatorWindowReductionActiveState(windowReductionActive);
            }

            // HumiditySensor
        } else if (capability.isTypeHumiditySensor()) {
            // when values are changed, they come with separate events
            // values should only updated when they are not null
            final Double humidity = event.getProperties().getHumidity();
            final Boolean moldWarning = event.getProperties().getMoldWarning();
            if (humidity != null) {
                capabilityState.setHumiditySensorHumidityState(humidity);
            }
            if (moldWarning != null) {
                capabilityState.setHumiditySensorMoldWarningState(moldWarning);
            }

            // WindowDoorSensor
        } else if (capability.isTypeWindowDoorSensor()) {
            capabilityState.setWindowDoorSensorState(event.getProperties().getIsOpen());

            // SmokeDetectorSensor
        } else if (capability.isTypeSmokeDetectorSensor()) {
            capabilityState.setSmokeDetectorSensorState(event.getProperties().getIsSmokeAlarm());

            // AlarmActuator
        } else if (capability.isTypeAlarmActuator()) {
            capabilityState.setAlarmActuatorState(event.getProperties().getOnState());

            // MotionDetectionSensor
        } else if (capability.isTypeMotionDetectionSensor()) {
            capabilityState.setMotionDetectionSensorState(event.getProperties().getMotionDetectedCount());

            // LuminanceSensor
        } else if (capability.isTypeLuminanceSensor()) {
            capabilityState.setLuminanceSensorState(event.getProperties().getLuminance());

            // PushButtonSensor
        } else if (capability.isTypePushButtonSensor() && event.isButtonPressedEvent()) {
            // Some devices send both StateChanged and ButtonPressed. But only one should be handled,
            // therefore it is checked for button pressed event (button index and LastPressedButtonIndex are set).
            final Integer buttonIndex = event.getProperties().getKeyPressButtonIndex();
            capabilityState.setPushButtonSensorButtonIndexState(buttonIndex);
            capabilityState.setPushButtonSensorButtonIndexType(event.getProperties().getKeyPressType());
            capabilityState.setPushButtonSensorCounterState(event.getProperties().getKeyPressCounter());

            // EnergyConsumptionSensor
        } else if (capability.isTypeEnergyConsumptionSensor()) {
            capabilityState.setEnergyConsumptionSensorEnergyConsumptionMonthKWhState(
                    event.getProperties().getEnergyConsumptionMonthKWh());
            capabilityState.setEnergyConsumptionSensorAbsoluteEnergyConsumptionState(
                    event.getProperties().getAbsoluteEnergyConsumption());
            capabilityState.setEnergyConsumptionSensorEnergyConsumptionMonthEuroState(
                    event.getProperties().getEnergyConsumptionMonthEuro());
            capabilityState.setEnergyConsumptionSensorEnergyConsumptionDayEuroState(
                    event.getProperties().getEnergyConsumptionDayEuro());
            capabilityState.setEnergyConsumptionSensorEnergyConsumptionDayKWhState(
                    event.getProperties().getEnergyConsumptionDayKWh());

            // PowerConsumptionSensor
        } else if (capability.isTypePowerConsumptionSensor()) {
            capabilityState.setPowerConsumptionSensorPowerConsumptionWattState(
                    event.getProperties().getPowerConsumptionWatt());

            // GenerationMeterEnergySensor
        } else if (capability.isTypeGenerationMeterEnergySensor()) {
            capabilityState.setGenerationMeterEnergySensorEnergyPerMonthInKWhState(
                    event.getProperties().getEnergyPerMonthInKWh());
            capabilityState.setGenerationMeterEnergySensorTotalEnergyState(event.getProperties().getTotalEnergy());
            capabilityState.setGenerationMeterEnergySensorEnergyPerMonthInEuroState(
                    event.getProperties().getEnergyPerMonthInEuro());
            capabilityState.setGenerationMeterEnergySensorEnergyPerDayInEuroState(
                    event.getProperties().getEnergyPerDayInEuro());
            capabilityState
                    .setGenerationMeterEnergySensorEnergyPerDayInKWhState(event.getProperties().getEnergyPerDayInKWh());

            // GenerationMeterPowerConsumptionSensor
        } else if (capability.isTypeGenerationMeterPowerConsumptionSensor()) {
            capabilityState
                    .setGenerationMeterPowerConsumptionSensorPowerInWattState(event.getProperties().getPowerInWatt());

            // TwoWayMeterEnergyConsumptionSensor
        } else if (capability.isTypeTwoWayMeterEnergyConsumptionSensor()) {
            capabilityState.setTwoWayMeterEnergyConsumptionSensorEnergyPerMonthInKWhState(
                    event.getProperties().getEnergyPerMonthInKWh());
            capabilityState
                    .setTwoWayMeterEnergyConsumptionSensorTotalEnergyState(event.getProperties().getTotalEnergy());
            capabilityState.setTwoWayMeterEnergyConsumptionSensorEnergyPerMonthInEuroState(
                    event.getProperties().getEnergyPerMonthInEuro());
            capabilityState.setTwoWayMeterEnergyConsumptionSensorEnergyPerDayInEuroState(
                    event.getProperties().getEnergyPerDayInEuro());
            capabilityState.setTwoWayMeterEnergyConsumptionSensorEnergyPerDayInKWhState(
                    event.getProperties().getEnergyPerDayInKWh());

            // TwoWayMeterEnergyFeedSensor
        } else if (capability.isTypeTwoWayMeterEnergyFeedSensor()) {
            capabilityState.setTwoWayMeterEnergyFeedSensorEnergyPerMonthInKWhState(
                    event.getProperties().getEnergyPerMonthInKWh());
            capabilityState.setTwoWayMeterEnergyFeedSensorTotalEnergyState(event.getProperties().getTotalEnergy());
            capabilityState.setTwoWayMeterEnergyFeedSensorEnergyPerMonthInEuroState(
                    event.getProperties().getEnergyPerMonthInEuro());
            capabilityState.setTwoWayMeterEnergyFeedSensorEnergyPerDayInEuroState(
                    event.getProperties().getEnergyPerDayInEuro());
            capabilityState
                    .setTwoWayMeterEnergyFeedSensorEnergyPerDayInKWhState(event.getProperties().getEnergyPerDayInKWh());

            // TwoWayMeterPowerConsumptionSensor
        } else if (capability.isTypeTwoWayMeterPowerConsumptionSensor()) {
            capabilityState
                    .setTwoWayMeterPowerConsumptionSensorPowerInWattState(event.getProperties().getPowerInWatt());

        } else {
            logger.debug("Unsupported capability type {}.", capability.getType());
            return false;
        }
        return true;
    }

    private void updateChannels(DeviceDTO device) {
        // DEVICE STATES
        final boolean isReachable = updateStatus(device);

        if (isReachable) {
            updateDeviceChannels(device);

            // CAPABILITY STATES
            for (final Entry<String, CapabilityDTO> entry : device.getCapabilityMap().entrySet()) {
                final CapabilityDTO capability = entry.getValue();

                logger.debug("->capability:{} ({}/{})", capability.getId(), capability.getType(), capability.getName());

                if (capability.hasState()) {
                    updateCapabilityChannels(device, capability);
                } else {
                    logger.debug("Capability not available for device {} ({})", device.getConfig().getName(),
                            device.getType());
                }
            }
        }
    }

    private boolean updateStatus(DeviceDTO device) {
        Boolean reachable = device.isReachable();
        if (reachable != null) {
            if (reachable) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Device not reachable.");
                return false;
            }
        }
        return true;
    }

    private void updateDeviceChannels(DeviceDTO device) {
        if (device.isBatteryPowered()) {
            if (device.hasLowBattery()) {
                updateState(CHANNEL_BATTERY_LOW, OnOffType.ON);
            } else {
                updateState(CHANNEL_BATTERY_LOW, OnOffType.OFF);
            }
        }
    }

    private void updateCapabilityChannels(DeviceDTO device, CapabilityDTO capability) {
        switch (capability.getType()) {
            case CapabilityDTO.TYPE_VARIABLEACTUATOR:
                updateVariableActuatorChannels(capability);
                break;
            case CapabilityDTO.TYPE_SWITCHACTUATOR:
                updateSwitchActuatorChannels(capability);
                break;
            case CapabilityDTO.TYPE_DIMMERACTUATOR:
                updateDimmerActuatorChannels(capability);
                break;
            case CapabilityDTO.TYPE_ROLLERSHUTTERACTUATOR:
                updateRollerShutterActuatorChannels(capability);
                break;
            case CapabilityDTO.TYPE_TEMPERATURESENSOR:
                updateTemperatureSensorChannels(capability);
                break;
            case CapabilityDTO.TYPE_THERMOSTATACTUATOR:
                updateThermostatActuatorChannels(device, capability);
                break;
            case CapabilityDTO.TYPE_HUMIDITYSENSOR:
                updateHumiditySensorChannels(capability);
                break;
            case CapabilityDTO.TYPE_WINDOWDOORSENSOR:
                updateWindowDoorSensorChannels(capability);
                break;
            case CapabilityDTO.TYPE_SMOKEDETECTORSENSOR:
                updateSmokeDetectorChannels(capability);
                break;
            case CapabilityDTO.TYPE_ALARMACTUATOR:
                updateAlarmActuatorChannels(capability);
                break;
            case CapabilityDTO.TYPE_MOTIONDETECTIONSENSOR:
                updateMotionDetectionSensorChannels(capability);
                break;
            case CapabilityDTO.TYPE_LUMINANCESENSOR:
                updateLuminanceSensorChannels(capability);
                break;
            case CapabilityDTO.TYPE_PUSHBUTTONSENSOR:
                updatePushButtonSensorChannels(capability);
                break;
            case CapabilityDTO.TYPE_ENERGYCONSUMPTIONSENSOR:
                updateEnergyConsumptionSensorChannels(capability);
                break;
            case CapabilityDTO.TYPE_POWERCONSUMPTIONSENSOR:
                updateStateForEnergyChannel(CHANNEL_POWER_CONSUMPTION_WATT,
                        capability.getCapabilityState().getPowerConsumptionSensorPowerConsumptionWattState(),
                        capability);
                break;
            case CapabilityDTO.TYPE_GENERATIONMETERENERGYSENSOR:
                updateGenerationMeterEnergySensorChannels(capability);
                break;
            case CapabilityDTO.TYPE_GENERATIONMETERPOWERCONSUMPTIONSENSOR:
                updateStateForEnergyChannel(CHANNEL_POWER_GENERATION_WATT,
                        capability.getCapabilityState().getGenerationMeterPowerConsumptionSensorPowerInWattState(),
                        capability);
                break;
            case CapabilityDTO.TYPE_TWOWAYMETERENERGYCONSUMPTIONSENSOR:
                updateTwoWayMeterEnergyConsumptionSensorChannels(capability);
                break;
            case CapabilityDTO.TYPE_TWOWAYMETERENERGYFEEDSENSOR:
                updateTwoWayMeterEnergyFeedSensorChannels(capability);
                break;
            case CapabilityDTO.TYPE_TWOWAYMETERPOWERCONSUMPTIONSENSOR:
                updateStateForEnergyChannel(CHANNEL_POWER_WATT,
                        capability.getCapabilityState().getTwoWayMeterPowerConsumptionSensorPowerInWattState(),
                        capability);
                break;
            default:
                logger.debug("Unsupported capability type {}.", capability.getType());
                break;
        }
    }

    private void updateVariableActuatorChannels(CapabilityDTO capability) {
        final Boolean variableActuatorState = capability.getCapabilityState().getVariableActuatorState();
        if (variableActuatorState != null) {
            updateState(CHANNEL_SWITCH, OnOffType.from(variableActuatorState));
        } else {
            logStateNULL(capability);
        }
    }

    private void updateSwitchActuatorChannels(CapabilityDTO capability) {
        final Boolean switchActuatorState = capability.getCapabilityState().getSwitchActuatorState();
        if (switchActuatorState != null) {
            updateState(CHANNEL_SWITCH, OnOffType.from(switchActuatorState));
        } else {
            logStateNULL(capability);
        }
    }

    private void updateDimmerActuatorChannels(CapabilityDTO capability) {
        final Integer dimLevel = capability.getCapabilityState().getDimmerActuatorState();
        if (dimLevel != null) {
            logger.debug("Dimlevel state {}", dimLevel);
            updateState(CHANNEL_DIMMER, new PercentType(dimLevel));
        } else {
            logStateNULL(capability);
        }
    }

    private void updateRollerShutterActuatorChannels(CapabilityDTO capability) {
        Integer rollerShutterLevel = capability.getCapabilityState().getRollerShutterActuatorState();
        if (rollerShutterLevel != null) {
            rollerShutterLevel = invertRollerShutterValueIfConfigured(rollerShutterLevel);
            logger.debug("RollerShutterlevel state {}", rollerShutterLevel);
            updateState(CHANNEL_ROLLERSHUTTER, new PercentType(rollerShutterLevel));
        } else {
            logStateNULL(capability);
        }
    }

    private void updateTemperatureSensorChannels(CapabilityDTO capability) {
        // temperature
        final Double temperature = capability.getCapabilityState().getTemperatureSensorTemperatureState();
        if (temperature != null) {
            logger.debug("-> Temperature sensor state: {}", temperature);
            updateState(CHANNEL_TEMPERATURE, new DecimalType(temperature));
        } else {
            logStateNULL(capability);
        }

        // frost warning
        final Boolean frostWarning = capability.getCapabilityState().getTemperatureSensorFrostWarningState();
        if (frostWarning != null) {
            updateState(CHANNEL_FROST_WARNING, OnOffType.from(frostWarning));
        } else {
            logStateNULL(capability);
        }
    }

    private void updateThermostatActuatorChannels(DeviceDTO device, CapabilityDTO capability) {
        // point temperature
        final Double pointTemperature = capability.getCapabilityState().getThermostatActuatorPointTemperatureState();
        if (pointTemperature != null) {
            logger.debug("Update CHANNEL_SET_TEMPERATURE: state:{} (DeviceName {}, Capab-ID:{})", pointTemperature,
                    device.getConfig().getName(), capability.getId());
            updateState(CHANNEL_SET_TEMPERATURE, new DecimalType(pointTemperature));
        } else {
            logStateNULL(capability);
        }

        // operation mode
        final String operationMode = capability.getCapabilityState().getThermostatActuatorOperationModeState();
        if (operationMode != null) {
            updateState(CHANNEL_OPERATION_MODE, new StringType(operationMode));
        } else {
            logStateNULL(capability);
        }

        // window reduction active
        final Boolean windowReductionActive = capability.getCapabilityState()
                .getThermostatActuatorWindowReductionActiveState();
        if (windowReductionActive != null) {
            updateState(CHANNEL_WINDOW_REDUCTION_ACTIVE, OnOffType.from(windowReductionActive));
        } else {
            logStateNULL(capability);
        }
    }

    private void updateHumiditySensorChannels(CapabilityDTO capability) {
        // humidity
        final Double humidity = capability.getCapabilityState().getHumiditySensorHumidityState();
        if (humidity != null) {
            updateState(CHANNEL_HUMIDITY, new DecimalType(humidity));
        } else {
            logStateNULL(capability);
        }

        // mold warning
        final Boolean moldWarning = capability.getCapabilityState().getHumiditySensorMoldWarningState();
        if (moldWarning != null) {
            updateState(CHANNEL_MOLD_WARNING, OnOffType.from(moldWarning));
        } else {
            logStateNULL(capability);
        }
    }

    private void updateWindowDoorSensorChannels(CapabilityDTO capability) {
        final Boolean contactState = capability.getCapabilityState().getWindowDoorSensorState();
        if (contactState != null) {
            updateState(CHANNEL_CONTACT, toOpenClosedType(contactState));
        } else {
            logStateNULL(capability);
        }
    }

    private void updateSmokeDetectorChannels(CapabilityDTO capability) {
        final Boolean smokeState = capability.getCapabilityState().getSmokeDetectorSensorState();
        if (smokeState != null) {
            updateState(CHANNEL_SMOKE, OnOffType.from(smokeState));
        } else {
            logStateNULL(capability);
        }
    }

    private void updateAlarmActuatorChannels(CapabilityDTO capability) {
        final Boolean alarmState = capability.getCapabilityState().getAlarmActuatorState();
        if (alarmState != null) {
            updateState(CHANNEL_ALARM, OnOffType.from(alarmState));
        } else {
            logStateNULL(capability);
        }
    }

    private void updateMotionDetectionSensorChannels(CapabilityDTO capability) {
        final Integer motionCount = capability.getCapabilityState().getMotionDetectionSensorState();
        if (motionCount != null) {
            logger.debug("Motion state {} -> count {}", motionCount, motionCount);
            updateState(CHANNEL_MOTION_COUNT, new DecimalType(motionCount));
        } else {
            logStateNULL(capability);
        }
    }

    private void updateLuminanceSensorChannels(CapabilityDTO capability) {
        final Double luminance = capability.getCapabilityState().getLuminanceSensorState();
        if (luminance != null) {
            updateState(CHANNEL_LUMINANCE, new DecimalType(luminance));
        } else {
            logStateNULL(capability);
        }
    }

    private void updatePushButtonSensorChannels(CapabilityDTO capability) {
        final Integer pushCount = capability.getCapabilityState().getPushButtonSensorCounterState();
        final Integer buttonIndex = capability.getCapabilityState().getPushButtonSensorButtonIndexState();
        logger.debug("Pushbutton index {} count {}", buttonIndex, pushCount);
        if (buttonIndex != null && pushCount != null) {
            if (buttonIndex >= 0 && buttonIndex <= 7) {
                final String type = capability.getCapabilityState().getPushButtonSensorButtonIndexType();
                if (type != null) {
                    final int channelIndex = buttonIndex + 1;
                    if (SHORT_PRESS.equals(type)) {
                        triggerChannel(CHANNEL_BUTTON + channelIndex, CommonTriggerEvents.SHORT_PRESSED);
                    } else if (LONG_PRESS.equals(type)) {
                        triggerChannel(CHANNEL_BUTTON + channelIndex, CommonTriggerEvents.LONG_PRESSED);
                    }
                    triggerChannel(CHANNEL_BUTTON + channelIndex, CommonTriggerEvents.PRESSED);
                    updateState(String.format(CHANNEL_BUTTON_COUNT, channelIndex), new DecimalType(pushCount));
                } else {
                    logger.debug("Button type NULL not supported.");
                }
            } else {
                logger.debug("Button index NULL not supported.");
            }
            // Button handled so remove state to avoid re-trigger.
            capability.getCapabilityState().setPushButtonSensorButtonIndexState(null);
            capability.getCapabilityState().setPushButtonSensorButtonIndexType(null);
        } else {
            logStateNULL(capability);
        }
    }

    private void updateEnergyConsumptionSensorChannels(CapabilityDTO capability) {
        updateStateForEnergyChannel(CHANNEL_ENERGY_CONSUMPTION_MONTH_KWH,
                capability.getCapabilityState().getEnergyConsumptionSensorEnergyConsumptionMonthKWhState(), capability);
        updateStateForEnergyChannel(CHANNEL_ABOLUTE_ENERGY_CONSUMPTION,
                capability.getCapabilityState().getEnergyConsumptionSensorAbsoluteEnergyConsumptionState(), capability);
        updateStateForEnergyChannel(CHANNEL_ENERGY_CONSUMPTION_MONTH_EURO,
                capability.getCapabilityState().getEnergyConsumptionSensorEnergyConsumptionMonthEuroState(),
                capability);
        updateStateForEnergyChannel(CHANNEL_ENERGY_CONSUMPTION_DAY_EURO,
                capability.getCapabilityState().getEnergyConsumptionSensorEnergyConsumptionDayEuroState(), capability);
        updateStateForEnergyChannel(CHANNEL_ENERGY_CONSUMPTION_DAY_KWH,
                capability.getCapabilityState().getEnergyConsumptionSensorEnergyConsumptionDayKWhState(), capability);
    }

    private void updateGenerationMeterEnergySensorChannels(CapabilityDTO capability) {
        updateStateForEnergyChannel(CHANNEL_ENERGY_GENERATION_MONTH_KWH,
                capability.getCapabilityState().getGenerationMeterEnergySensorEnergyPerMonthInKWhState(), capability);
        updateStateForEnergyChannel(CHANNEL_TOTAL_ENERGY_GENERATION,
                capability.getCapabilityState().getGenerationMeterEnergySensorTotalEnergyState(), capability);
        updateStateForEnergyChannel(CHANNEL_ENERGY_GENERATION_MONTH_EURO,
                capability.getCapabilityState().getGenerationMeterEnergySensorEnergyPerMonthInEuroState(), capability);
        updateStateForEnergyChannel(CHANNEL_ENERGY_GENERATION_DAY_EURO,
                capability.getCapabilityState().getGenerationMeterEnergySensorEnergyPerDayInEuroState(), capability);
        updateStateForEnergyChannel(CHANNEL_ENERGY_GENERATION_DAY_KWH,
                capability.getCapabilityState().getGenerationMeterEnergySensorEnergyPerDayInKWhState(), capability);
    }

    private void updateTwoWayMeterEnergyConsumptionSensorChannels(CapabilityDTO capability) {
        updateStateForEnergyChannel(CHANNEL_ENERGY_MONTH_KWH,
                capability.getCapabilityState().getTwoWayMeterEnergyConsumptionSensorEnergyPerMonthInKWhState(),
                capability);
        updateStateForEnergyChannel(CHANNEL_TOTAL_ENERGY,
                capability.getCapabilityState().getTwoWayMeterEnergyConsumptionSensorTotalEnergyState(), capability);
        updateStateForEnergyChannel(CHANNEL_ENERGY_MONTH_EURO,
                capability.getCapabilityState().getTwoWayMeterEnergyConsumptionSensorEnergyPerMonthInEuroState(),
                capability);
        updateStateForEnergyChannel(CHANNEL_ENERGY_DAY_EURO,
                capability.getCapabilityState().getTwoWayMeterEnergyConsumptionSensorEnergyPerDayInEuroState(),
                capability);
        updateStateForEnergyChannel(CHANNEL_ENERGY_DAY_KWH,
                capability.getCapabilityState().getTwoWayMeterEnergyConsumptionSensorEnergyPerDayInKWhState(),
                capability);
    }

    private void updateTwoWayMeterEnergyFeedSensorChannels(CapabilityDTO capability) {
        updateStateForEnergyChannel(CHANNEL_ENERGY_FEED_MONTH_KWH,
                capability.getCapabilityState().getTwoWayMeterEnergyFeedSensorEnergyPerMonthInKWhState(), capability);
        updateStateForEnergyChannel(CHANNEL_TOTAL_ENERGY_FED,
                capability.getCapabilityState().getTwoWayMeterEnergyFeedSensorTotalEnergyState(), capability);
        updateStateForEnergyChannel(CHANNEL_ENERGY_FEED_MONTH_EURO,
                capability.getCapabilityState().getTwoWayMeterEnergyFeedSensorEnergyPerMonthInEuroState(), capability);
        updateStateForEnergyChannel(CHANNEL_ENERGY_FEED_DAY_EURO,
                capability.getCapabilityState().getTwoWayMeterEnergyFeedSensorEnergyPerDayInEuroState(), capability);
        updateStateForEnergyChannel(CHANNEL_ENERGY_FEED_DAY_KWH,
                capability.getCapabilityState().getTwoWayMeterEnergyFeedSensorEnergyPerDayInKWhState(), capability);
    }

    private void updateStateForEnergyChannel(final String channelId, @Nullable final Double state,
            final CapabilityDTO capability) {
        if (state != null) {
            final DecimalType newValue = new DecimalType(state);
            updateState(channelId, newValue);
        } else {
            logStateNULL(capability);
        }
    }

    /**
     * Returns the inverted value. Currently only rollershutter channels are supported.
     *
     * @param value value to become inverted
     * @return the value or the inverted value
     */
    private int invertRollerShutterValueIfConfigured(final int value) {
        @Nullable
        final Channel channel = getThing().getChannel(CHANNEL_ROLLERSHUTTER);
        if (channel == null) {
            logger.debug("Channel {} was null! Value not inverted.", CHANNEL_ROLLERSHUTTER);
            return value;
        }
        final Boolean invert = (Boolean) channel.getConfiguration().get("invert");
        if (invert != null && invert) {
            return value;
        }
        return 100 - value;
    }

    /**
     * Returns the {@link DeviceDTO} associated with this {@link LivisiDeviceHandler} (referenced by the
     * {@link LivisiDeviceHandler#deviceId}).
     *
     * @return the {@link DeviceDTO} or null, if not found or no {@link LivisiBridgeHandler} is available
     */
    private Optional<DeviceDTO> getDevice() {
        return getBridgeHandler().flatMap(bridgeHandler -> bridgeHandler.getDeviceById(deviceId));
    }

    private Optional<LivisiBridgeHandler> registerAtBridgeHandler() {
        synchronized (this.lock) {
            if (this.bridgeHandler == null) {
                @Nullable
                final Bridge bridge = getBridge();
                if (bridge == null) {
                    return Optional.empty();
                }
                @Nullable
                final ThingHandler handler = bridge.getHandler();
                if (handler instanceof LivisiBridgeHandler) {
                    this.bridgeHandler = (LivisiBridgeHandler) handler;
                    this.bridgeHandler.registerDeviceStatusListener(deviceId, this);
                } else {
                    return Optional.empty(); // also called when the handler is NULL
                }
            }
            return getBridgeHandler();
        }
    }

    /**
     * Returns the LIVISI bridge handler.
     *
     * @return the {@link LivisiBridgeHandler} or null
     */
    private Optional<LivisiBridgeHandler> getBridgeHandler() {
        return Optional.ofNullable(this.bridgeHandler);
    }

    private @Nullable ThingStatus getBridgeStatus() {
        @Nullable
        Bridge bridge = getBridge();
        if (bridge != null) {
            return getBridge().getStatus();
        }
        return null;
    }

    private void logStateNULL(CapabilityDTO capability) {
        logger.debug("State for {} is STILL NULL!! cstate-id: {}, capability-id: {}", capability.getType(),
                capability.getCapabilityState().getId(), capability.getId());
    }

    private static OpenClosedType toOpenClosedType(boolean isOpen) {
        if (isOpen) {
            return OpenClosedType.OPEN;
        }
        return OpenClosedType.CLOSED;
    }
}
