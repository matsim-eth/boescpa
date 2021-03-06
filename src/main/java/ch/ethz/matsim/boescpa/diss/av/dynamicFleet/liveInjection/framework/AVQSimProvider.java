package ch.ethz.matsim.boescpa.diss.av.dynamicFleet.liveInjection.framework;

import ch.ethz.matsim.av.config.AVConfig;
import ch.ethz.matsim.av.dispatcher.AVDispatchmentListener;
import ch.ethz.matsim.av.schedule.AVOptimizer;
import ch.ethz.matsim.boescpa.diss.av.dynamicFleet.liveInjection.vrpagent.VrpAgentSourceIndividualAgent;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.dvrp.passenger.PassengerEngine;
import org.matsim.contrib.dvrp.vrpagent.VrpAgentSource;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.framework.Mobsim;
import org.matsim.core.mobsim.qsim.AbstractQSimPlugin;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.QSimUtils;

import java.util.Collection;

public class AVQSimProvider implements Provider<Mobsim> {
    @Inject private EventsManager eventsManager;
    @Inject private Collection<AbstractQSimPlugin> plugins;
    @Inject private Scenario scenario;

    @Inject private Injector injector;
    @Inject private AVConfig config;

    @Override
    public Mobsim get() {
        QSim qSim = QSimUtils.createQSim(scenario, eventsManager, plugins);
        Injector childInjector = injector.createChildInjector(new AVQSimModule(config, qSim));

        qSim.addQueueSimulationListeners(childInjector.getInstance(AVOptimizer.class));
        qSim.addQueueSimulationListeners(childInjector.getInstance(AVDispatchmentListener.class));

        qSim.addMobsimEngine(childInjector.getInstance(PassengerEngine.class));
        qSim.addDepartureHandler(childInjector.getInstance(PassengerEngine.class));
        qSim.addAgentSource(childInjector.getInstance(VrpAgentSource.class));
        qSim.addAgentSource(childInjector.getInstance(VrpAgentSourceIndividualAgent.class));

        return qSim;
    }
}
