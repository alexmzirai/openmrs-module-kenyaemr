package org.openmrs.module.kenyaemr.calculation.library.hiv;

import org.openmrs.Obs;
import org.openmrs.Program;
import org.openmrs.api.context.Context;
import org.openmrs.calculation.patient.PatientCalculationContext;
import org.openmrs.calculation.result.CalculationResultMap;
import org.openmrs.calculation.result.ListResult;
import org.openmrs.calculation.result.SimpleResult;
import org.openmrs.module.kenyacore.calculation.AbstractPatientCalculation;
import org.openmrs.module.kenyacore.calculation.CalculationUtils;
import org.openmrs.module.kenyacore.calculation.Calculations;
import org.openmrs.module.kenyacore.calculation.Filters;
import org.openmrs.module.kenyaemr.Dictionary;
import org.openmrs.module.kenyaemr.calculation.EmrCalculationUtils;
import org.openmrs.module.kenyaemr.calculation.library.hiv.art.InitialArtStartDateCalculation;
import org.openmrs.module.kenyaemr.metadata.HivMetadata;
import org.openmrs.module.metadatadeploy.MetadataUtils;
import org.openmrs.module.reporting.common.Age;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Calculates the date when a patient was medically eligible and the reasons
 */
public class DateMedicallyEligibleForARTCalculation extends AbstractPatientCalculation {

    @Override
    public CalculationResultMap evaluate(Collection<Integer> cohort, Map<String, Object> parameterValues, PatientCalculationContext context) {

        CalculationResultMap ret = new CalculationResultMap();
        Program hivProgram = MetadataUtils.existing(Program.class, HivMetadata._Program.HIV);

        Set<Integer> inHivProgram = Filters.inProgram(hivProgram, cohort, context);

        CalculationResultMap ages = Calculations.ages(cohort, context);

        CalculationResultMap allWhoStage = Calculations.allObs(Dictionary.getConcept(Dictionary.CURRENT_WHO_STAGE), cohort, context);
        CalculationResultMap allCd4 = Calculations.allObs(Dictionary.getConcept(Dictionary.CD4_COUNT), cohort, context);
        CalculationResultMap allCd4Percent = Calculations.allObs(Dictionary.getConcept(Dictionary.CD4_PERCENT), cohort, context);
        CalculationResultMap artStartDateMap = calculate(new InitialArtStartDateCalculation(), cohort, context);

        for (Integer ptId : cohort) {
            String dateAndReason = null;
            if (inHivProgram.contains(ptId)) {
                int ageInMonths = ((Age) ages.get(ptId).getValue()).getFullMonths();
                Date birthDate = Context.getPersonService().getPerson(ptId).getBirthdate();
                List<Obs> whoStages = CalculationUtils.extractResultValues((ListResult) allWhoStage.get(ptId));
                List<Obs> cd4s = CalculationUtils.extractResultValues((ListResult) allCd4.get(ptId));
                List<Obs> cd4Percents = CalculationUtils.extractResultValues((ListResult) allCd4Percent.get(ptId));
                Date arvStartDate = EmrCalculationUtils.datetimeResultForPatient(artStartDateMap, ptId);

                dateAndReason = isEligible(ageInMonths, cd4s, cd4Percents, whoStages, birthDate, arvStartDate);
            }
            ret.put(ptId, new SimpleResult(dateAndReason, this));
        }
        return ret;
    }

    /**
     * Checks eligibility based on age, CD4 and WHO stage
     * @param ageInMonths the patient age in months
     * @param cd4 the last CD4 count
     * @param cd4Percent the last CD4 percentage
     * @param whoStage the last WHO stage
     * @param birthDate the date when born
     * @param arvStartDate
     * @return true if patient is eligible
     */
    protected String isEligible(int ageInMonths, List<Obs> cd4, List<Obs> cd4Percent, List<Obs> whoStage, Date birthDate, Date arvStartDate) {
        String dateAndReason = null;

        if (ageInMonths < 24) {
            dateAndReason = birthDate+"="+"7";
        }
        else if (ageInMonths < 60) { // 24-59 months
            if (whoStage != null && (!cd4.isEmpty())) {
                for(Obs obsWhoStage:whoStage) {
                    if (obsWhoStage.getValueCoded().equals(Dictionary.WHO_STAGE_3_PEDS)) {
                        dateAndReason = (arvStartDate != null && obsWhoStage.getObsDatetime().after(arvStartDate))? arvStartDate + "=" : obsWhoStage.getObsDatetime() + "=" + "1" ;
                    }

                    if (obsWhoStage.getValueCoded().equals(Dictionary.WHO_STAGE_4_PEDS)) {

                        dateAndReason = (arvStartDate != null && obsWhoStage.getObsDatetime().after(arvStartDate))? arvStartDate + "=" : obsWhoStage.getObsDatetime() + "=" + "1" ;

                    }
                }

            }
            if (cd4Percent != null && (!cd4Percent.isEmpty())) {
                for(Obs obsPercent:cd4Percent) {
                    if(obsPercent.getValueNumeric() < 25) {
                        dateAndReason = (arvStartDate != null && obsPercent.getObsDatetime().after(arvStartDate))? arvStartDate + "=" : obsPercent.getObsDatetime() + "=" + "2" ;
                        break;
                    }
                }
            }
            if (cd4 != null && (!cd4.isEmpty())) {
                for(Obs obsCd4 : cd4) {
                    if(obsCd4.getValueNumeric() < 1000) {
                        dateAndReason = (arvStartDate != null && obsCd4.getObsDatetime().after(arvStartDate))? arvStartDate + "=" : obsCd4.getObsDatetime() + "=" + "2" ;
                        break;
                    }
                }
            }
        }
        else if (ageInMonths < 155) { // 5-12 years
			if ( whoStage != null && (!whoStage.isEmpty()) && (!cd4Percent.isEmpty() || !cd4.isEmpty() )) {
				Map<String, Date> reason = new HashMap<String, Date>();
				for(Obs obsWhoStage:whoStage) {
					if (obsWhoStage.getValueCoded().equals(Dictionary.WHO_STAGE_3_PEDS) || obsWhoStage.getValueCoded().equals(Dictionary.WHO_STAGE_4_PEDS)) {

						Date date = (arvStartDate != null && obsWhoStage.getObsDatetime().after(arvStartDate))? arvStartDate : obsWhoStage.getObsDatetime();
						String r = "1";
						reason.put(r, date);
						break;
					}
				}
				for(Obs obsPercent : cd4Percent) {
					if (obsPercent.getValueNumeric() < 20) {
						Date date = (arvStartDate != null && obsPercent.getObsDatetime().after(arvStartDate))? arvStartDate : obsPercent.getObsDatetime();
						String r = "2";
						reason.put(r, date);
						break;
					}
				}
				for(Obs obsCd4 : cd4) {
					if (obsCd4.getValueNumeric() < 500) {
						Date date = (arvStartDate != null && obsCd4.getObsDatetime().after(arvStartDate))? arvStartDate : obsCd4.getObsDatetime() ;
						String r = "3";
						reason.put(r, date);
						break;
					}
				}
				String iReason = null;
				if (reason.keySet().size() == 1){
					String rK = new ArrayList<String>(reason.keySet()).get(0);
					Date date = reason.get(rK);
					iReason = date + "=" + rK;
				} else if (reason.keySet().containsAll(Arrays.asList("1", "2", "3"))) {
					Date wDate = reason.get("1");
					Date cDate = reason.get("2");
					Date pDate = reason.get("3");
					if (wDate.before(cDate) || wDate.before(pDate)) {
						iReason = wDate + "=" + "1";
					} else if (cDate.after(pDate)) {
						iReason = pDate + "=" + "2";
					} else if (pDate.after(cDate)){
						iReason = cDate + "=" + "2";
					} else {
						iReason = cDate + "=" + "2";
					}
				} else if (reason.keySet().size()==2 && !reason.keySet().contains("1")) {

					Date cDate = reason.get("2");
					Date pDate = reason.get("3");
					if (cDate.after(pDate)) {
						iReason = pDate + "=" + "2";
					} else if (pDate.after(cDate)){
						iReason = cDate + "=" + "2";
					} else {
						iReason = cDate + "=" + "2";
					}
				} else if (reason.keySet().size()==2 && reason.keySet().contains("1")) {
					Date wDate = reason.get("1");
					String otherKey =null;
					Date otherDate = null;
					for (String s : reason.keySet()) {
						if (!s.equals("1")){
							otherKey = s;
							otherDate = reason.get(otherKey);
							break;
						}
					}


					if (wDate.after(otherDate)) {
						iReason = otherDate + "=" + otherKey;
					}  else {
						iReason = wDate + "=" + "1";
					}
				}
				dateAndReason = iReason;
			}
            if (whoStage != null && (!whoStage.isEmpty())) {
                for(Obs obsWhoStage:whoStage) {
                    if (obsWhoStage.getValueCoded().equals(Dictionary.WHO_STAGE_3_PEDS)) {
                        dateAndReason = (arvStartDate != null && obsWhoStage.getObsDatetime().after(arvStartDate))? arvStartDate + "=" : obsWhoStage.getObsDatetime() + "=" + "1" ;
                    }
                    if (obsWhoStage.getValueCoded().equals(Dictionary.WHO_STAGE_4_PEDS)) {
                        dateAndReason = (arvStartDate != null && obsWhoStage.getObsDatetime().after(arvStartDate))? arvStartDate + "=" : obsWhoStage.getObsDatetime() + "=" + "1" ;
                    }
                    break;
                }
            }
            if (cd4Percent != null && (!cd4Percent.isEmpty())) {
                for(Obs obsPercent : cd4Percent) {
                    if (obsPercent.getValueNumeric() < 20) {
                        dateAndReason = (arvStartDate != null && obsPercent.getObsDatetime().after(arvStartDate))? arvStartDate + "=" : obsPercent.getObsDatetime() + "=" + "2" ;
                        break;
                    }
                }
            }
            if (cd4 != null && (!cd4.isEmpty())) {
                for(Obs obsCd4 : cd4) {
                    if (obsCd4.getValueNumeric() < 500) {
                        dateAndReason = (arvStartDate != null && obsCd4.getObsDatetime().after(arvStartDate))? arvStartDate + "=" : obsCd4.getObsDatetime() + "=" + "2" ;
                        break;
                    }
                }
            }
        }
        else { // 13+ years
			if ( whoStage != null && (!whoStage.isEmpty()) && (!cd4Percent.isEmpty() || !cd4.isEmpty() )) {
				Map<String, Date> reason = new HashMap<String, Date>();
				for(Obs obsWhoStage:whoStage) {
					if (obsWhoStage.getValueCoded().equals(Dictionary.WHO_STAGE_3_PEDS) || obsWhoStage.getValueCoded().equals(Dictionary.WHO_STAGE_4_PEDS)) {

						Date date = (arvStartDate != null && obsWhoStage.getObsDatetime().after(arvStartDate))? arvStartDate : obsWhoStage.getObsDatetime();
						String r = "1";
						reason.put(r, date);
						break;
					}
				}
				for(Obs obsPercent : cd4Percent) {
					if (obsPercent.getValueNumeric() < 20) {
						Date date = (arvStartDate != null && obsPercent.getObsDatetime().after(arvStartDate))? arvStartDate : obsPercent.getObsDatetime();
						String r = "2";
						reason.put(r, date);
						break;
					}
				}
				for(Obs obsCd4 : cd4) {
					if (obsCd4.getValueNumeric() < 500) {
						Date date = (arvStartDate != null && obsCd4.getObsDatetime().after(arvStartDate))? arvStartDate : obsCd4.getObsDatetime() ;
						String r = "3";
						reason.put(r, date);
						break;
					}
				}
				String iReason = null;
				if (reason.keySet().size() == 1){
					String rK = new ArrayList<String>(reason.keySet()).get(0);
					Date date = reason.get(rK);
					iReason = date + "=" + rK;
				} else if (reason.keySet().containsAll(Arrays.asList("1", "2", "3"))) {
					Date wDate = reason.get("1");
					Date cDate = reason.get("2");
					Date pDate = reason.get("3");
					if (wDate.before(cDate) || wDate.before(pDate)) {
						iReason = wDate + "=" + "1";
					} else if (cDate.after(pDate)) {
						iReason = pDate + "=" + "2";
					} else if (pDate.after(cDate)){
						iReason = cDate + "=" + "2";
					} else {
						iReason = cDate + "=" + "2";
					}
				} else if (reason.keySet().size()==2 && !reason.keySet().contains("1")) {

					Date cDate = reason.get("2");
					Date pDate = reason.get("3");
					if (cDate.after(pDate)) {
						iReason = pDate + "=" + "2";
					} else if (pDate.after(cDate)){
						iReason = cDate + "=" + "2";
					} else {
						iReason = cDate + "=" + "2";
					}
				} else if (reason.keySet().size()==2 && reason.keySet().contains("1")) {
					Date wDate = reason.get("1");
					String otherKey =null;
					Date otherDate = null;
					for (String s : reason.keySet()) {
						if (!s.equals("1")){
							otherKey = s;
							otherDate = reason.get(otherKey);
							break;
						}
					}


					if (wDate.after(otherDate)) {
						iReason = otherDate + "=" + otherKey;
					}  else {
						iReason = wDate + "=" + "1";
					}
				}
				dateAndReason = iReason;
			}
            if (whoStage != null && (!whoStage.isEmpty())) {
                for(Obs obsWhoStage:whoStage) {
                    if (obsWhoStage.getValueCoded().equals(Dictionary.WHO_STAGE_3_ADULT)) {
                        dateAndReason = (arvStartDate != null && obsWhoStage.getObsDatetime().after(arvStartDate))? arvStartDate + "=" : obsWhoStage.getObsDatetime() + "=" + "1" ;
                    }
                    if (obsWhoStage.getValueCoded().equals(Dictionary.WHO_STAGE_4_ADULT)) {
                        dateAndReason = (arvStartDate != null && obsWhoStage.getObsDatetime().after(arvStartDate))? arvStartDate + "=" : obsWhoStage.getObsDatetime() + "=" + "1" ;
                    }
                    break;
                }
            }
            if (cd4 != null && (!cd4.isEmpty())) {
                for(Obs obsCd4:cd4) {
                    if(obsCd4.getValueNumeric() < 350) {
                        dateAndReason = (arvStartDate != null && obsCd4.getObsDatetime().after(arvStartDate))? arvStartDate + "=" : obsCd4.getObsDatetime() + "=" + "2" ;
                        break;
                    }
                }
            }
        }
        return dateAndReason;
    }


}