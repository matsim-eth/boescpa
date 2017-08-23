package ch.ethz.matsim.boescpa.diss.av.dynamicFleet.scoring;

import ch.ethz.matsim.av.config.AVConfig;
import ch.ethz.matsim.av.scoring.AVScoringFunction;
import ch.ethz.matsim.boescpa.diss.av.dynamicFleet.framework.IVTAVModule;
import ch.ethz.matsim.boescpa.diss.baseline.scoring.IVTBaselineScoringFunctionFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.StageActivityTypesImpl;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.core.scoring.functions.SubpopulationScoringParameters;
import org.matsim.pt.PtConstants;

@Singleton
public class IVTAVScoringFunctionFactory implements ScoringFunctionFactory {
	final private AVConfig config;
	final private ScoringFunctionFactory standardFactory;
	final private ScoringParametersForPerson params;

	@Inject
    public IVTAVScoringFunctionFactory(Scenario scenario, AVConfig config) {
		this.config = config;
        params = new SubpopulationScoringParameters(scenario);
        standardFactory = new IVTBaselineScoringFunctionFactory(scenario, new StageActivityTypesImpl(PtConstants.TRANSIT_ACTIVITY_TYPE));
    }
    
	@Override
	public ScoringFunction createNewScoringFunction(Person person) {
		SumScoringFunction sf = (SumScoringFunction) standardFactory.createNewScoringFunction(person);

		double marginalUtilityOfMoney = params.getScoringParameters(person).marginalUtilityOfMoney;
		double marginalUtilityOfTraveling = params.getScoringParameters(person)
                .modeParams.get(IVTAVModule.AV_MODE).marginalUtilityOfTraveling_s;

        sf.addScoringFunction(new AVScoringFunction(config, person, marginalUtilityOfMoney, marginalUtilityOfTraveling));

		return sf;
	}
}
