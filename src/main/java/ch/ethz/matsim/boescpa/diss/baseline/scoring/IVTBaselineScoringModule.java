package ch.ethz.matsim.boescpa.diss.baseline.scoring;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.router.StageActivityTypesImpl;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.pt.PtConstants;

public class IVTBaselineScoringModule extends AbstractModule {
    @Override
    public void install() {
        bind(ScoringFunctionFactory.class).to(IVTBaselineScoringFunctionFactory.class);
    }

    @Provides @Singleton
    public IVTBaselineScoringFunctionFactory provideIVTBaselineScoringFunctionFactory(Scenario scenario) {
        return new IVTBaselineScoringFunctionFactory(scenario, new StageActivityTypesImpl(PtConstants.TRANSIT_ACTIVITY_TYPE));
    }
}
