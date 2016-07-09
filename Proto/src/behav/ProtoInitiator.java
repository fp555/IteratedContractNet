/** Author: Fabio on 01/06/2016 */
package behav;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.DataStore;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.states.MsgReceiver;

import java.util.*;

import static jade.lang.acl.MessageTemplate.MatchPerformative;

public class ProtoInitiator extends FSMBehaviour {
    /** stato per preparare le cfp */
    private static final String PREPARE_CFP_STATE = "Prepare-Cfp";
    /** stato per ricevere le propose/refuse */
    private static final String RECEIVE_PR_STATE = "Receive-PR";
    /** stato per ricevere le agree/propose */
    private static final String RECEIVE_AP_STATE = "Receive-AP";
    /** stato per ricevere le inform */
    private static final String RECEIVE_INFORM_STATE = "Receive-Inform";
    /** stato per controllare le sessioni */
    private static final String CHECK_SESSIONS = "Check-Sessions";
    /** stato finale per terminare il protocollo */
    private static final String FINAL = "Final";

    /** chiave per selezionare il vettore di chiavi di sessione dei cfp */
    private final String ALL_CFP_K = "__all-cfp" + hashCode();
    /** chiave per selezionare la map di chiavi di sessione delle propose */
    private final String ALL_PROPOSE_K = "__all-propose" + hashCode();
    /** chiave per selezionare l'ultimo messaggio ricevuto */
    private final String REPLY_K = "__reply" + hashCode();

    /** deadline per ricevere il prossimo messaggio */
    private long receiveDeadline = MsgReceiver.INFINITE;
    /** messagetemplate dei receiver */
    private MessageTemplate msgTemplate;
    /** risorse massime dell'agente */
    private int risorseMax;

    public ProtoInitiator(Agent a, int max, final DFAgentDescription[] ads){
        super(a);
        risorseMax = max;
        setDataStore(new DataStore());

        //creiamo la map nel datastore che associerà i convID ai costi proposti (da ciascun partecipante)
        getDataStore().put(ALL_PROPOSE_K, new HashMap<ACLMessage, Integer>());

        //registrazione transizioni
        registerDefaultTransition(PREPARE_CFP_STATE, RECEIVE_PR_STATE);
        registerTransition(RECEIVE_PR_STATE, RECEIVE_PR_STATE, -1, new String[]{RECEIVE_PR_STATE});
        registerTransition(RECEIVE_PR_STATE, CHECK_SESSIONS, 0);
        registerTransition(RECEIVE_PR_STATE, FINAL, MsgReceiver.TIMEOUT_EXPIRED);
        registerTransition(CHECK_SESSIONS, RECEIVE_AP_STATE, 0);
        registerTransition(CHECK_SESSIONS, FINAL, -1);
        registerTransition(RECEIVE_AP_STATE, RECEIVE_PR_STATE, ACLMessage.PROPOSE, new String[]{
                RECEIVE_PR_STATE, CHECK_SESSIONS, RECEIVE_AP_STATE});
        registerTransition(RECEIVE_AP_STATE, CHECK_SESSIONS, ACLMessage.REJECT_PROPOSAL, new String[]{
                CHECK_SESSIONS, RECEIVE_AP_STATE});
        registerTransition(RECEIVE_AP_STATE, CHECK_SESSIONS, MsgReceiver.TIMEOUT_EXPIRED, new String[]{
                CHECK_SESSIONS, RECEIVE_AP_STATE});
        registerTransition(RECEIVE_AP_STATE, RECEIVE_INFORM_STATE, ACLMessage.ACCEPT_PROPOSAL);
        registerDefaultTransition(RECEIVE_INFORM_STATE, FINAL);

        //registazione stati
        Behaviour b = new OneShotBehaviour(myAgent) {
            @Override public void action() {
                Vector v = new Vector(ads.length); //il vettore di sessionkey da salvare nel datastore
                ACLMessage cfp = new ACLMessage(ACLMessage.CFP); //la cfp da clonare
                cfp.setConversationId(createNewConvID()); //lo stesso per tutti i destinatari

                //aggiusto il msgtemplate
                setMsgTemplate(MessageTemplate.and(MessageTemplate.or(MatchPerformative(
                        ACLMessage.PROPOSE), MatchPerformative(ACLMessage.REFUSE)),
                        MessageTemplate.MatchConversationId(cfp.getConversationId())));

                cfp.setReplyByDate(new Date(System.currentTimeMillis() + 30000)); //30sec deadline
                receiveDeadline = cfp.getReplyByDate().getTime();
                ((MsgReceiver) getState(RECEIVE_PR_STATE)).setDeadline(receiveDeadline);

                //settiamo tutti i destinatari (cfp unica)
                for(DFAgentDescription ad: ads) {
                    cfp.addReceiver(ad.getName());
                    v.add(ad.getName().getLocalName());
                    System.out.println(myAgent.getLocalName() + ": sto inviando una cfp a " + ad.getName().getLocalName());
                }
                myAgent.send(cfp);
                getDataStore().put(ALL_CFP_K, v);
            }
        };
        b.setDataStore(getDataStore());
        registerFirstState(b, PREPARE_CFP_STATE);

        b = new MsgReceiver(myAgent, getMsgTemplate(), receiveDeadline, getDataStore(), REPLY_K) {
            @Override protected void handleMessage(ACLMessage msg) {
                if(msg.getPerformative() == ACLMessage.PROPOSE) {
                    ((HashMap) getDataStore().get(ALL_PROPOSE_K)).put(msg, Integer.parseInt(msg.getContent()));
                    System.out.println(myAgent.getLocalName() + ": ho ricevuto una proposta da " +
                            msg.getSender().getLocalName() + " per un costo di " + msg.getContent());
                }
                //togliamo la sessionkey delle cfp del partecipante che ci ha appena risposto
                ((Vector) getDataStore().get(ALL_CFP_K)).remove(msg.getSender().getLocalName());
            }

            @Override public int onEnd() {
                //se il vettore di partecipanti è diventato vuoto abbiamo ricevuto tutte le propose e possimo procedere
                //altrimenti dobbiamo rilanciare di nuovo questo stato per ricevere un'altra propose
                return (((Vector) getDataStore().get(ALL_CFP_K)).isEmpty()) ? 0 : -1;
            }
        };
        registerState(b, RECEIVE_PR_STATE);

        b = new OneShotBehaviour(myAgent) {
            private int ret;
            @Override public void action() {
                //selezioniamo il migliore a cui fare la propose
                Collection c = ((HashMap) getDataStore().get(ALL_PROPOSE_K)).values();
                ACLMessage winner = null;

                //troviamo la chiave (ACLMessage) corrispondente al costo minimo
                for(Object entry: ((HashMap) getDataStore().get(ALL_PROPOSE_K)).entrySet())
                    if(Objects.equals(((Map.Entry) entry).getValue(), Collections.min(c))) {
                        winner = (ACLMessage) ((Map.Entry) entry).getKey();
                        break;
                    }
                // prepariamo la request da inviare al vincitore
                ACLMessage reply;
                if(winner != null) {
                    System.out.println(myAgent.getLocalName() + ": la proposta migliore è di " +
                            winner.getSender().getLocalName() + " al costo di " + winner.getContent());

                    //tolgo il vincitore dalla map
                    HashMap propose = ((HashMap) getDataStore().get(ALL_PROPOSE_K));
                    propose.remove(winner);
                    getDataStore().put(ALL_PROPOSE_K, propose); //se non faccio sta copia pare che non va...
                    reply = winner.createReply();
                    reply.setContent(winner.getContent()); //ci rimetto il costo
                    reply.setPerformative(ACLMessage.REQUEST);
                    reply.setReplyByDate(new Date(System.currentTimeMillis() + 30000)); //30sec deadline
                    receiveDeadline = reply.getReplyByDate().getTime();
                    ((MsgReceiver) getState(RECEIVE_AP_STATE)).setDeadline(receiveDeadline);

                    //setto il msgtemplate per il receiver delle agree
                    setMsgTemplate(MessageTemplate.and(MessageTemplate.or(MatchPerformative(
                            ACLMessage.PROPOSE), MatchPerformative(ACLMessage.AGREE)),
                            MessageTemplate.MatchConversationId(winner.getConversationId())));
                    myAgent.send(reply);
                    System.out.println(myAgent.getLocalName() + ": ho inviato la request a " +
                            ((AID) (reply.getAllReceiver().next())).getLocalName());
                    ret = 0;
                }
                else ret = -1; //sono finiti i responder da interpellare
            }

            @Override public int onEnd() {
                return ret;
            }
        };
        b.setDataStore(getDataStore());
        registerState(b, CHECK_SESSIONS);

        b = new MsgReceiver(myAgent,getMsgTemplate(), receiveDeadline, getDataStore(), REPLY_K) {
            private int ret;
            @Override protected void handleMessage(ACLMessage msg) {
                switch(msg.getPerformative()) {
                    case ACLMessage.AGREE:
                        System.out.println(myAgent.getLocalName() + ": l'agente " + msg.getSender().getLocalName() +
                                " ha confermato la sua proposta");
                        ACLMessage reply = msg.createReply();
                        reply.setContent(msg.getContent()); //ci rimetto il costo
                        reply.setReplyByDate(new Date(System.currentTimeMillis() + 30000)); //30sec deadline
                        receiveDeadline = reply.getReplyByDate().getTime();
                        ((MsgReceiver) getState(RECEIVE_INFORM_STATE)).setDeadline(receiveDeadline);

                        //decidiamo se possiamo accettare oppure no
                        if(Integer.parseInt(msg.getContent()) < risorseMax) { //accettiamo
                            System.out.println(myAgent.getLocalName() + " ha accettato definitivamente la proposta di " +
                                    msg.getSender().getLocalName() + " al costo di " + msg.getContent());
                            reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                            setMsgTemplate(MessageTemplate.and(MatchPerformative(ACLMessage.INFORM),
                                    MessageTemplate.MatchConversationId(msg.getConversationId())));

                            //dobbaimo mandiamo reject a tutti gli altri che ancora aspettano
                            ACLMessage reject = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
                            for(Object entry: ((HashMap) getDataStore().get(ALL_PROPOSE_K)).keySet())
                                reject.addReceiver(((ACLMessage) entry).getSender());
                            ((HashMap) getDataStore().get(ALL_PROPOSE_K)).clear();
                            reject.setConversationId(msg.getConversationId());
                            myAgent.send(reject);
                        }
                        else { //rifiutiamo -> dovremo chiedere a qualcun altro
                            System.out.println(myAgent.getLocalName() + " ha rifiutato la proposta di " +
                                    msg.getSender().getLocalName() + " al costo di " + msg.getContent());
                            reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
                        }
                        ret = reply.getPerformative();
                        myAgent.send(reply);
                        break;
                    case ACLMessage.PROPOSE:
                        ((HashMap) getDataStore().get(ALL_PROPOSE_K)).put(msg, Integer.parseInt(msg.getContent()));
                        ret = ACLMessage.PROPOSE;
                        break;
                }
            }
            @Override public int onEnd() {
                return ret;
            }
        };
        registerState(b, RECEIVE_AP_STATE);

        b = new MsgReceiver(myAgent, getMsgTemplate(), receiveDeadline, getDataStore(), REPLY_K) {
            @Override protected void handleMessage(ACLMessage msg) {
                super.handleMessage(msg);
                System.out.println(myAgent.getLocalName() + ": " + msg.getSender().getLocalName() +
                        " ha completato la mia richiesta al costo di " + msg.getContent());
            }
        };
        registerState(b, RECEIVE_INFORM_STATE);

        b = new OneShotBehaviour(myAgent) {
            @Override public void action() {
                System.out.println(myAgent.getLocalName() + ": ho terminato il protocollo");
            }
        };
        b.setDataStore(getDataStore());
        registerLastState(b, FINAL);
    }

    /** per generare numeri autoincrement */
    private static int cnt = 0;
    private synchronized String createNewConvID() {
        return "C" + hashCode() + myAgent.getLocalName() + System.currentTimeMillis() + "_" + Integer.toString(++cnt);
    }

    private MessageTemplate getMsgTemplate(){
        return msgTemplate;
    }

    private void setMsgTemplate(MessageTemplate mt) {
        msgTemplate=mt;
    }
}
