package com.lineguard.simulation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lineguard.simulation")
public class SimulationProperties {

    private boolean enabled = true;
    private long tickMillis = 700;
    private double stepMeters = 120;
    private int minimumDepartureBattery = 40;
    private int criticalBattery = 10;
    private int dockingMaxRetries = 2;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getTickMillis() { return tickMillis; }
    public void setTickMillis(long tickMillis) { this.tickMillis = tickMillis; }
    public double getStepMeters() { return stepMeters; }
    public void setStepMeters(double stepMeters) { this.stepMeters = stepMeters; }
    public int getMinimumDepartureBattery() { return minimumDepartureBattery; }
    public void setMinimumDepartureBattery(int minimumDepartureBattery) { this.minimumDepartureBattery = minimumDepartureBattery; }
    public int getCriticalBattery() { return criticalBattery; }
    public void setCriticalBattery(int criticalBattery) { this.criticalBattery = criticalBattery; }
    public int getDockingMaxRetries() { return dockingMaxRetries; }
    public void setDockingMaxRetries(int dockingMaxRetries) { this.dockingMaxRetries = dockingMaxRetries; }
}

