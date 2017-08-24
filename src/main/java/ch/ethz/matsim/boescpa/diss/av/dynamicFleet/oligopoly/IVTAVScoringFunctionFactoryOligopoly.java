package ch.ethz.matsim.boescpa.diss.av.dynamicFleet.oligopoly;

import ch.ethz.matsim.av.config.AVConfig;
import ch.ethz.matsim.av.scoring.AVScoringFunction;
import ch.ethz.matsim.boescpa.diss.av.dynamicFleet.framework.IVTAVModule;
import ch.ethz.matsim.boescpa.diss.baseline.scoring.IndividualVOTConfig;
import com.google.inject.Singleton;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.socnetsim.jointtrips.scoring.BlackListedActivityScoringFunction;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.ScenarioConfigGroup;
import org.matsim.core.router.StageActivityTypes;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.scoring.functions.*;
import org.matsim.households.Household;
import org.matsim.households.PersonHouseholdMapping;
import org.matsim.utils.objectattributes.ObjectAttributes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Singleton
public class IVTAVScoringFunctionFactoryOligopoly implements ScoringFunctionFactory {
	private final Scenario scenario;
	private final StageActivityTypes blackList;
	private final double votElasticity;
	private final double referenceIncome;
	final private AVConfig avConfig;

	// very expensive to initialize:only do once!
	private final Map<Id, ScoringParameters> individualParameters = new HashMap<>();
	private final PersonHouseholdMapping personHouseholdMapping;

	// /////////////////////////////////////////////////////////////////////////
	// constructors
	// /////////////////////////////////////////////////////////////////////////
	public IVTAVScoringFunctionFactoryOligopoly(
			final Scenario scenario,
			final StageActivityTypes typesNotToScore,
			final AVConfig avConfig) {
		this.scenario = scenario;
		this.blackList = typesNotToScore;
		this.personHouseholdMapping = new PersonHouseholdMapping(scenario.getHouseholds());
		IndividualVOTConfig individualVOTConfig =
				(IndividualVOTConfig) scenario.getConfig().getModules().get(IndividualVOTConfig.NAME);
		this.votElasticity = individualVOTConfig.getVotElasticity();
		this.referenceIncome = individualVOTConfig.getReferenceHouseholdIncome();
		this.avConfig = avConfig;
	}

	@Override
	public ScoringFunction createNewScoringFunction(final Person person) {
		// get scenario elements at the lattest possible, to be sure all is initialized
		final PlanCalcScoreConfigGroup planCalcScoreConfig = scenario.getConfig().planCalcScore();
		final ObjectAttributes personAttributes = scenario.getPopulation().getPersonAttributes();

		final SumScoringFunction scoringFunctionAccumulator = new SumScoringFunction();
		final ScoringParameters params =
				createParams( person , planCalcScoreConfig , scenario.getConfig().scenario(), personAttributes );

		// activities
		scoringFunctionAccumulator.addScoringFunction(
				new BlackListedActivityScoringFunction(blackList,
						new CharyparNagelActivityScoring(params, new FacilityOpeningIntervalCalculator(scenario.getActivityFacilities()))));
		//Logger.getLogger( CharyparNagelActivityScoring.class ).setLevel( Level.ERROR );

		// legs
		scoringFunctionAccumulator.addScoringFunction(new CharyparNagelLegScoring(params, scenario.getNetwork()));
		scoringFunctionAccumulator.addScoringFunction(new AVScoringFunctionOligopoly(avConfig, person, planCalcScoreConfig.getMarginalUtilityOfMoney(), params.modeParams.get(IVTAVModule.AV_MODE).marginalUtilityOfTraveling_s, Math.pow(personHouseholdMapping.getHousehold(person.getId()).getIncome().getIncome() / referenceIncome, votElasticity)));

		// other standard stuff
		scoringFunctionAccumulator.addScoringFunction(new CharyparNagelMoneyScoring(params));
		scoringFunctionAccumulator.addScoringFunction(new CharyparNagelAgentStuckScoring(params));

		return scoringFunctionAccumulator;
	}

	private ScoringParameters createParams(
			final Person person,
			final PlanCalcScoreConfigGroup config,
			final ScenarioConfigGroup scenarioConfig,
			final ObjectAttributes personAttributes) {
		if ( individualParameters.containsKey( person.getId() ) ) {
			return individualParameters.get( person.getId() );
		}

		// Individual activity parameters:
		final ScoringParameters.Builder builder =
				new ScoringParameters.Builder(config, config.getScoringParameters(null),
						scenarioConfig);
		final Set<String> handledTypes = new HashSet<>();
		for ( Activity act : TripStructureUtils.getActivities( person.getSelectedPlan() , blackList ) ) {
			// XXX works only if no variation of type of activities between plans
			if ( !handledTypes.add( act.getType() ) ) continue; // parameters already gotten

			final String id = person.getId().toString();

			// I am not so pleased with this, as wrong parameters may silently be
			// used (for instance if individual preferences are ill-specified).
			// This should become nicer once we have a better format for specifying
			// utility parameters in the config.
			final ActivityUtilityParameters.Builder typeBuilder =
					new ActivityUtilityParameters.Builder(
							config.getActivityParams( act.getType() ) != null ?
									config.getActivityParams( act.getType() ) :
									new PlanCalcScoreConfigGroup.ActivityParams( act.getType() ) );

			final Double earliestEndTime =
					(Double) personAttributes.getAttribute(
							id,
							"earliestEndTime_"+act.getType() );
			if ( earliestEndTime != null ) {
				typeBuilder.setScoreAtAll(true);
				typeBuilder.setEarliestEndTime( earliestEndTime );
			}

			final Double latestStartTime =
					(Double) personAttributes.getAttribute(
							id,
							"latestStartTime_"+act.getType() );
			if ( latestStartTime != null ) {
				typeBuilder.setScoreAtAll(true);
				typeBuilder.setLatestStartTime(latestStartTime);
			}

			final Double minimalDuration =
					(Double) personAttributes.getAttribute(
							id,
							"minimalDuration_"+act.getType() );
			if ( minimalDuration != null ) {
				typeBuilder.setScoreAtAll( true );
				typeBuilder.setMinimalDuration(minimalDuration);
			}

			final Double typicalDuration =
					(Double) personAttributes.getAttribute(
							id,
							"typicalDuration_"+act.getType() );
			if ( typicalDuration != null ) {
				typeBuilder.setScoreAtAll( true );
				typeBuilder.setTypicalDuration_s(typicalDuration);
			}

			builder.setActivityParameters(
					act.getType(),
					typeBuilder );
		}

		// Individual VOT parameters:

		Household household = personHouseholdMapping.getHousehold(person.getId());
		if (household != null) {
			final double income = household.getIncome().getIncome();
			final double votCorrectionFactor = Math.pow(income / referenceIncome, votElasticity);
			final double hrToSec = 1 / 3600.0;

			builder.setMarginalUtilityOfEarlyDeparture_s(
					config.getEarlyDeparture_utils_hr() * hrToSec * votCorrectionFactor);
			builder.setMarginalUtilityOfLateArrival_s(
					config.getLateArrival_utils_hr() * hrToSec * votCorrectionFactor);
			builder.setMarginalUtilityOfPerforming_s(
					config.getPerforming_utils_hr() * hrToSec * votCorrectionFactor);
			builder.setMarginalUtilityOfWaiting_s(
					config.getMarginalUtlOfWaiting_utils_hr() * hrToSec * votCorrectionFactor);
			builder.setMarginalUtilityOfWaitingPt_s(
					config.getMarginalUtlOfWaitingPt_utils_hr() * hrToSec * votCorrectionFactor);

			for (String mode : config.getModes().keySet()) {
				PlanCalcScoreConfigGroup.ModeParams modeParams = config.getOrCreateModeParams(mode);
				final ModeUtilityParameters.Builder modeBuilder =
						new ModeUtilityParameters.Builder(modeParams);
				modeBuilder.setMarginalUtilityOfTraveling_s(
						modeParams.getMarginalUtilityOfTraveling() * hrToSec * votCorrectionFactor);
				builder.setModeParameters(mode, modeBuilder);
			}
		}

		final ScoringParameters params =
				builder.build();
		individualParameters.put( person.getId() , params );
		return params;
	}
}
