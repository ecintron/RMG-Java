////////////////////////////////////////////////////////////////////////////////
//
//	RMG - Reaction Mechanism Generator
//
//	Copyright (c) 2002-2009 Prof. William H. Green (whgreen@mit.edu) and the
//	RMG Team (rmg_dev@mit.edu)
//
//	Permission is hereby granted, free of charge, to any person obtaining a
//	copy of this software and associated documentation files (the "Software"),
//	to deal in the Software without restriction, including without limitation
//	the rights to use, copy, modify, merge, publish, distribute, sublicense,
//	and/or sell copies of the Software, and to permit persons to whom the
//	Software is furnished to do so, subject to the following conditions:
//
//	The above copyright notice and this permission notice shall be included in
//	all copies or substantial portions of the Software.
//
//	THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//	IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//	FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//	AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//	LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
//	FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
//	DEALINGS IN THE SOFTWARE.
//
////////////////////////////////////////////////////////////////////////////////

package jing.rxnSys;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import jing.chem.Species;
import jing.param.Temperature;
import jing.rxn.PDepIsomer;
import jing.rxn.PDepKineticsEstimator;
import jing.rxn.PDepNetwork;
import jing.rxn.PDepReaction;
import jing.rxn.Reaction;

/**
 * A rate-based reaction model enlarger for use when pressure-dependent
 * kinetics estimation is desired. In addition to checking the species fluxes,
 * this class contains a piece of the activated species algorithm (ASA), a
 * method for ensuring that pressure-dependent networks are being explored in
 * sufficient detail.
 * @author jwallen
 */
public class RateBasedPDepRME implements ReactionModelEnlarger {

	/**
	 * The pressure-dependent kinetics estimator to use. Currently this will
	 * only hold a FastMasterEqn object, as the Chemdis class has been
	 * depreciated. This is the object called when a pressure-dependent
	 * calculation is run.
	 */
	private PDepKineticsEstimator pDepKineticsEstimator;
	
    //==========================================================================
	//
	//	Constructors
	//
    
    /**
	 * Default constructor. Does not set the pressure-dependent kinetics 
	 * estimator.
	 */
	public RateBasedPDepRME() {
		pDepKineticsEstimator = null;
    }
    
	//==========================================================================
	//
	//	Accessors
	//
    
	/**
	 * Returns the current pressure-dependent kinetics estimator.
	 * @return The current pressure-dependent kinetics estimator
	 */
	public PDepKineticsEstimator getPDepKineticsEstimator() {
		return pDepKineticsEstimator;
	}
	
	/**
	 * Sets the  pressure-dependent kinetics estimator.
	 * @param pdke The new pressure-dependent kinetics estimator
	 */
	public void setPDepKineticsEstimator(PDepKineticsEstimator pdke) {
		pDepKineticsEstimator = pdke;
	}

	/**
	 * Enlarges the reaction model by either adding a species to the core or
	 * making a unimolecular isomer included in a PDepNetwork. The action taken
	 * is based on the fluxes of species.
	 * @param rxnSystemList The reaction systems in the simulation
	 * @param rm The current reaction model in the simulation
	 * @param validList A boolean list of the validity status of each reaction system
	 */
	public void enlargeReactionModel(LinkedList rxnSystemList, ReactionModel rm, LinkedList validList) {
		
		CoreEdgeReactionModel cerm = (CoreEdgeReactionModel) rm;
		
		// Iterate over reaction systems, enlarging each individually
		LinkedList updateList = new LinkedList();
        for (int i = 0; i < rxnSystemList.size(); i++) {
			
			// Don't need to enlarge if the system is already valid
			if ((Boolean) validList.get(i))
				continue;
				
			ReactionSystem rxnSystem = (ReactionSystem) rxnSystemList.get(i);
                        PresentStatus ps = rxnSystem.getPresentStatus();

			// Get Rmin
			double Rmin = rxnSystem.getRmin();

			// Determine flux of all species (combining both pDep and non-pDep systems)
			int len = cerm.getMaxSpeciesID() + 1;
			double[] flux = new double[len];
			for (int n = 0; n < len; n++)
				flux[n] = 0.0;

			// Flux from non-pDep and P-dep reactions
			double[] unreactedSpeciesFlux = ps.getUnreactedSpeciesFlux();//unreacted species flux includes flux from both p-dep and non-pdep rxns: cf. appendUnreactedSpeciesStatus in ReactionSystem
			for (Iterator iter = cerm.getUnreactedSpeciesSet().iterator(); iter.hasNext(); ) {
				Species us = (Species) iter.next();
				if (us.getID() < unreactedSpeciesFlux.length)
					flux[us.getID()] = unreactedSpeciesFlux[us.getID()];
				else
					System.out.println("Warning: Attempted to read unreacted species flux for " +
							us.getName() + "(" + us.getID() + "), but there are only " +
							unreactedSpeciesFlux.length + " fluxes.");
			}

			// Flux from pDep reactions //gmagoon 61709: ...is already taken into account above (the unreactedSpeciesFlux is also used by the validityTester
//			for (Iterator iter = PDepNetwork.getNetworks().iterator(); iter.hasNext(); ) {
//				PDepNetwork pdn = (PDepNetwork) iter.next();
//				for (Iterator iter2 = pdn.getNetReactions().iterator(); iter2.hasNext(); ) {
//					PDepReaction rxn = (PDepReaction) iter2.next();
//					double forwardFlux = rxn.calculateForwardFlux(ps);
//					double reverseFlux = rxn.calculateReverseFlux(ps);
//					//System.out.println(rxn.toString() + ": " + forwardFlux + " " + reverseFlux);
//					for (int j = 0; j < rxn.getReactantNumber(); j++) {
//						Species species = (Species) rxn.getReactantList().get(j);
//						if (cerm.containsAsUnreactedSpecies(species))
//							flux[species.getID()] += reverseFlux;
//					}
//					for (int j = 0; j < rxn.getProductNumber(); j++) {
//						Species species = (Species) rxn.getProductList().get(j);
//						if (cerm.containsAsUnreactedSpecies(species))
//							flux[species.getID()] += forwardFlux;
//					}
//				}
//			}
			
			// Determine species with maximum flux and its flux
			Species maxSpecies = null;
			double maxFlux = 0;
			for (Iterator iter = cerm.getUnreactedSpeciesSet().iterator(); iter.hasNext(); ) {
				Species us = (Species) iter.next();
				if (Math.abs(flux[us.getID()]) >= maxFlux) {
					maxFlux = Math.abs(flux[us.getID()]);
					maxSpecies = us;
				}
			}
			if (maxSpecies == null) throw new NullPointerException();
			
			// Determine network with maximum leak flux and its flux
			PDepNetwork maxNetwork = null;
			double maxLeak = 0;
			for (Iterator iter = PDepNetwork.getNetworks().iterator(); iter.hasNext(); ) {
				PDepNetwork pdn = (PDepNetwork) iter.next();
				if (Math.abs(pdn.getLeakFlux(ps)) >= maxLeak) {
					maxLeak = Math.abs(pdn.getLeakFlux(ps));
					maxNetwork = pdn;
				}
			}
			if (maxNetwork == null) throw new NullPointerException();

			// Output results of above calculations to console
			System.out.print("Time: ");
			System.out.println(ps.getTime());
			System.out.println("Rmin: " + String.valueOf(Rmin));
			System.out.println("Unreacted species " + maxSpecies.getName() + " has highest flux: " + String.valueOf(maxFlux));
			System.out.println("Network " + maxNetwork.getID() + " has highest leak flux: " + String.valueOf(maxLeak));

			//if (maxFlux > Rmin)
			if (maxFlux > maxLeak && maxFlux > Rmin) {
				if (!updateList.contains(maxSpecies))
					updateList.add(maxSpecies);
				else
					updateList.add(null);
			}
			else if (maxLeak > Rmin) {
				if (!updateList.contains(maxNetwork))
					updateList.add(maxNetwork);
				else
					updateList.add(null);
			}
			else
				updateList.add(null);

		}

		for (int i = 0; i < updateList.size(); i++) {

			Object object = updateList.get(i);
			ReactionSystem rxnSystem = (ReactionSystem) rxnSystemList.get(i);
			PresentStatus ps = rxnSystem.getPresentStatus();

			if (object == null)
				continue;
			else if (object instanceof Species) {

				Species maxSpecies = (Species) object;

				// Add a species to the core
				System.out.print("\nAdd a new reacted Species: ");
				System.out.println(maxSpecies.getChemkinName());
				System.out.println(maxSpecies.toStringWithoutH());
				Temperature temp = new Temperature(715, "K");
				double H = maxSpecies.calculateH(temp);
				double S = maxSpecies.calculateS(temp);
				double G = maxSpecies.calculateG(temp);
				double Cp = maxSpecies.calculateCp(temp);
				System.out.println("Thermo of species at 715K (H, S, G, Cp, respectively)\t" + String.valueOf(H) + '\t' + String.valueOf(S) + '\t' + String.valueOf(G) + '\t' + String.valueOf(Cp));

				if (cerm.containsAsReactedSpecies(maxSpecies))
					System.out.println("Species " + maxSpecies.getName() + "(" +
							Integer.toString(maxSpecies.getID()) +
							") is already present in reaction model");
				else {

					// Move the species and appropriate reactions from the edge to the core
					cerm.moveFromUnreactedToReactedSpecies(maxSpecies);
					cerm.moveFromUnreactedToReactedReaction();

					// Generate new reaction set; partition into core and edge
					LinkedHashSet newReactionSet = rxnSystem.getReactionGenerator().react(cerm.getReactedSpeciesSet(),maxSpecies);
					newReactionSet.addAll(rxnSystem.getLibraryReactionGenerator().react(cerm.getReactedSpeciesSet(),maxSpecies));
					Iterator rxnIter = newReactionSet.iterator();
					while (rxnIter.hasNext()){
						Reaction r = (Reaction) rxnIter.next();
						if (r.getReactantNumber() > 1 && r.getProductNumber() > 1)
							cerm.addReaction(r);
						else {
							cerm.categorizeReaction(r.getStructure());
							PDepNetwork.addReactionToNetworks(r);
						}
					}

					// Also make species included in all PDepNetworks for which the species is a unimolecular isomer
					for (Iterator iter = PDepNetwork.getNetworks().iterator(); iter.hasNext(); ) {
						PDepNetwork pdn = (PDepNetwork) iter.next();
						if (pdn.contains(maxSpecies))
							pdn.makeIsomerIncluded(pdn.getIsomer(maxSpecies));
					}
				}

			}
			else if (object instanceof PDepNetwork) {

				PDepNetwork maxNetwork = (PDepNetwork) object;

				PDepIsomer isomer = maxNetwork.getMaxLeakIsomer(ps);
				System.out.println("\nAdd a new included Species: " + isomer.toString() +
						" to network " + maxNetwork.getID());

				maxNetwork.makeIsomerIncluded(isomer);

			}
			else
				continue;
			
			System.out.println("");
        }
    
	}
}
