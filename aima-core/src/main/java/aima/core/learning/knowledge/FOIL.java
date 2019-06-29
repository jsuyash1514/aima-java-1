package aima.core.learning.knowledge;

import aima.core.logic.fol.inference.InferenceResult;
import aima.core.logic.fol.inference.proof.Proof;
import aima.core.logic.fol.kb.FOLKnowledgeBase;
import aima.core.logic.fol.kb.FOLKnowledgeBaseFactory;
import aima.core.logic.fol.kb.data.Clause;
import aima.core.logic.fol.kb.data.Literal;
import aima.core.logic.fol.parsing.ast.Constant;
import aima.core.logic.fol.parsing.ast.Predicate;
import aima.core.logic.fol.parsing.ast.Term;
import aima.core.logic.fol.parsing.ast.Variable;
import aima.core.util.math.permute.PermutationGenerator;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class FOIL {
	
	public FOLKnowledgeBase kb = FOLKnowledgeBaseFactory.familyKnowledgeBase();
	
	public HashSet<Clause> foil(List<List<HashMap<Variable, Constant>>> examples, Literal target) {
		HashSet<Clause> clauses = new HashSet<>();
		while (!examples.get(0).isEmpty()) {
			Clause clause = newClause(examples, target);
			// remove positive examples covered by clause from examples
			for (HashMap<Variable, Constant> example : new ArrayList<>(examples.get(0))) {
				boolean covered = true;
				for (Literal l : clause.getLiterals()) {
					Map<Variable, Term> tempExample = new HashMap<>(example);
					if ((!l.getAtomicSentence().getSymbolicName().equals(target.getAtomicSentence().getSymbolicName())) && !kb.ask(kb.subst(tempExample, l).toString()).isTrue()) {
						covered = false;
						break;
					}
				}
				if (covered) {
					examples.get(0).remove(example);
				}
			}
			// add clause to clauses
			System.out.println(clause);
			clauses.add(clause);
		}
		return clauses;
	}
	
	private Clause newClause(List<List<HashMap<Variable, Constant>>> examples, Literal target) {
		Clause clause = new Clause(new ArrayList<>(Collections.singletonList(target)));
		Literal l;
		List<List<HashMap<Variable, Constant>>> extendedExamples = new ArrayList<>();
		extendedExamples.add(0, new ArrayList<>());
		extendedExamples.add(1, new ArrayList<>());
		for (HashMap<Variable, Constant> h : examples.get(0)) {
			HashMap<Variable, Constant> hashMap = new HashMap<>(h);
			extendedExamples.get(0).add(hashMap);
		}
		for (HashMap<Variable, Constant> h : examples.get(1)) {
			HashMap<Variable, Constant> hashMap = new HashMap<>(h);
			extendedExamples.get(1).add(hashMap);
		}
		while (!extendedExamples.get(1).isEmpty()) {
			l = chooseLiteral(newLiterals(clause), extendedExamples);
			System.out.println("CHOSEN LITERAL **** = " + l.toString());
			clause.addLiteral(l);
			List<HashMap<Variable, Constant>> allPosExamples = new ArrayList<>(extendedExamples.get(0));
			extendedExamples.get(0).clear();
			for (HashMap<Variable, Constant> example : allPosExamples) {
				extendedExamples.get(0).addAll(extendExample(example, l));
			}
			List<HashMap<Variable, Constant>> allNegExamples = new ArrayList<>(extendedExamples.get(1));
			extendedExamples.get(1).clear();
			for (HashMap<Variable, Constant> example : allNegExamples) {
				extendedExamples.get(1).addAll(extendExample(example, l));
			}
			System.out.println("Reduced size ==" + extendedExamples.get(1).size());
		}
		return clause;
	}
	
	private List<HashMap<Variable, Constant>> extendExample(HashMap<Variable, Constant> example, Literal literal) {
		List<HashMap<Variable, Constant>> extended_examples = new ArrayList<>();
		Map<Variable, Term> tempEg = new HashMap<>(example);
		InferenceResult result = kb.ask(kb.subst(tempEg, literal).toString());
		if (result.isTrue()) {
			for (Proof proof : result.getProofs()) {
				Map<Variable, Term> tempCondition = proof.getAnswerBindings();
				HashMap<Variable, Constant> h = new HashMap<>(example);
				for (Variable v : tempCondition.keySet()) {
					h.put(v, new Constant(tempCondition.get(v).toString()));
				}
				extended_examples.add(h);
			}
		}
		return extended_examples;
	}
	
	private Literal chooseLiteral(List<Literal> literals, List<List<HashMap<Variable, Constant>>> examples) {
		Literal literal = null;
		double gain = Double.MIN_VALUE;
		
		for(Literal l : literals){
			
			List<List<HashMap<Variable, Constant>>> allExamples = new ArrayList<>();
			allExamples.add(0, new ArrayList<>());
			allExamples.add(1, new ArrayList<>());
			for (HashMap<Variable, Constant> h : examples.get(0)) {
				HashMap<Variable, Constant> hashMap = new HashMap<>(h);
				allExamples.get(0).add(hashMap);
			}
			for (HashMap<Variable, Constant> h : examples.get(1)) {
				HashMap<Variable, Constant> hashMap = new HashMap<>(h);
				allExamples.get(1).add(hashMap);
			}
			
			double t = allExamples.get(0).size() + allExamples.get(1).size();
			double tplus = allExamples.get(0).size();
			
			double  icurr = -((Math.log(tplus/t))/(Math.log(2)));
			
			List<List<HashMap<Variable, Constant>>> extendedExamples = new ArrayList<>();
			extendedExamples.add(0, new ArrayList<>());
			extendedExamples.add(1, new ArrayList<>());
			int d = 0;
			
			for (HashMap<Variable, Constant> example : allExamples.get(0)) {
				Map<Variable, Term> tempEg = new HashMap<>(example);
				InferenceResult result = kb.ask(kb.subst(tempEg, l).toString());
				if(result.isTrue()) d++;
				extendedExamples.get(0).addAll(extendExample(example, l));
			}
			for (HashMap<Variable, Constant> example : allExamples.get(1)) {
				extendedExamples.get(1).addAll(extendExample(example, l));
			}
			
			double tdash = extendedExamples.get(0).size() + extendedExamples.get(1).size();
			double tdashplus = extendedExamples.get(0).size();
			
			double inow = -((Math.log(tdashplus/tdash))/(Math.log(2)));
			
			double g = d * (icurr - inow);
			if (g >= gain) {
				gain = g;
				literal = l;
			}
		}
		return literal;
	}
	
	private List<Literal> newLiterals(Clause clause) {
		HashSet<Literal> setOfLiterals = new HashSet<>();
		HashSet<Term> vars = new HashSet<>();
		for (Literal l : clause.getLiterals()) {
			vars.addAll(l.getAtomicSentence().getArgs());
		}
		for (String s : kb.getIndexFacts().keySet()) {
			Literal l = kb.getIndexFacts().get(s).get(0);
			int r = l.getAtomicSentence().getArgs().size();
			HashSet<Term> arg = new HashSet<>();
			arg.addAll(vars);
			int initialSize = arg.size();
			while (arg.size() < (initialSize + r - 1)) {
				arg.add(new Variable(Character.toString((char) (ThreadLocalRandom.current().nextInt(97, 123)))));
			}
			for (List<Term> variableList : PermutationGenerator.generatePermutations(new ArrayList<>(arg), r)) {
				Literal toAdd = new Literal(new Predicate(l.getAtomicSentence().getSymbolicName(), variableList), l.isNegativeLiteral());
				setOfLiterals.add(toAdd);
			}
		}
		System.out.println("New Literals: " + setOfLiterals.toString());
		return new ArrayList<>(setOfLiterals);
	}
	
}
