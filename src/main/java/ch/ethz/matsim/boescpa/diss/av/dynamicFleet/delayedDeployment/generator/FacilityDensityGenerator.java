package ch.ethz.matsim.boescpa.diss.av.dynamicFleet.delayedDeployment.generator;

import ch.ethz.matsim.av.config.AVGeneratorConfig;
import ch.ethz.matsim.av.data.AVVehicle;
import ch.ethz.matsim.av.framework.AVModule;
import ch.ethz.matsim.av.generator.AVGenerator;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.dvrp.data.Vehicle;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;
import org.opengis.feature.simple.SimpleFeature;

import java.util.*;

public class FacilityDensityGenerator implements AVGenerator {
    private final long numberOfVehicles;
	private long generatedNumberOfVehicles = 0;

    private final String prefix;

    private Map<Link, Double> cumulativeDensity = new LinkedHashMap<>();

    public FacilityDensityGenerator(AVGeneratorConfig config, Network network,
									ActivityFacilities facilities) {
        this.numberOfVehicles = config.getNumberOfVehicles();
		final CoordAnalyzer coordAnalyzer = new CoordAnalyzer(config.getPathToSHP(), network);
        String prefix = config.getPrefix();
        this.prefix = prefix == null ? "av_" + config.getParent().getId().toString() + "_" : prefix + "_";

        // Determine density
        long sum = 0;
		Set<Link> linkSet = new LinkedHashSet<>();
        Map<Link, Integer> density = new HashMap<>();
        for (ActivityFacility facility : facilities.getFacilities().values()) {
			Link link = network.getLinks().get(facility.getLinkId());
			if (coordAnalyzer.isLinkInArea(link)) {
				int linkDensity = density.getOrDefault(link, 0);
				density.put(link, ++linkDensity);
				linkSet.add(link);
				sum++;
			}
        }

        // Compute relative frequencies and cumulative
        double cumsum = 0.0;
        for (Link link : linkSet) {
            cumsum += density.get(link) / ((double)sum);
            cumulativeDensity.put(link, cumsum);
        }
    }

    @Override
    public boolean hasNext() {
        return generatedNumberOfVehicles < numberOfVehicles;
    }

    @Override
    public AVVehicle next() {
        generatedNumberOfVehicles++;

        // Multinomial selection
        double r = MatsimRandom.getRandom().nextDouble();
        Link selectedLink = null;
        for (Link link : cumulativeDensity.keySet()) {
            if (r <= cumulativeDensity.get(link)) {
                selectedLink = link;
                break;
            }
        }

        Id<Vehicle> id = Id.create(
        		"av_" + prefix + String.valueOf(generatedNumberOfVehicles), Vehicle.class);
        return new AVVehicle(id, selectedLink, 4.0, 0.0, 108000.0);
    }

    static public class Factory implements AVGeneratorFactory {
        @Inject @Named(AVModule.AV_MODE) private Network network;
        @Inject private ActivityFacilities facilities;

        @Override
        public AVGenerator createGenerator(AVGeneratorConfig generatorConfig) {
            return new FacilityDensityGenerator(generatorConfig, network, facilities);
        }
    }

    static private class CoordAnalyzer {
        private final Geometry area;
        private final GeometryFactory factory;
        private final Map<Id, Boolean> linkCache;

        private CoordAnalyzer(String pathToSHP, Network network) {
			this.linkCache = new HashMap<>();
        	if (pathToSHP != null) {
				Set<SimpleFeature> features = new HashSet<>();
				features.addAll(ShapeFileReader.getAllFeatures(pathToSHP));
				this.area = mergeGeometries(features);
				this.factory = new GeometryFactory();
			} else {
        		for (Id<Link> linkId : network.getLinks().keySet()) {
					this.linkCache.put(linkId, true);
				}
				this.area = null;
				this.factory = null;
			}
        }

        boolean isLinkInArea(Link link) {
            Boolean inArea = linkCache.get(link.getId());
            if (inArea == null) {
                inArea = area.contains(
                        factory.createPoint(new Coordinate(link.getCoord().getX(), link.getCoord().getY())));
                linkCache.put(link.getId(), inArea);
            }
            return inArea;
        }

        private Geometry mergeGeometries(Set<SimpleFeature> features) {
            Geometry geometry = null;
            for (SimpleFeature feature : features) {
                if (geometry == null) {
                    geometry = (Geometry) ((Geometry) feature.getDefaultGeometry()).clone();
                } else {
                    geometry = geometry.union((Geometry) feature.getDefaultGeometry());
                }
            }
            return geometry;
        }
    }
}
