package net.talpidae.base.util.names;

import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Random;


@Singleton
public class InsectNameGenerator extends NameGenerator
{
    private static final String[] syllables = {
            "-ac",
            "-blat",
            "-co",
            "-ca",
            "-ce",
            "-cer",
            "-cla",
            "-col",
            "-da +c",
            "-der",
            "-di",
            "-dis",
            "-dip",
            "-em",
            "-e +c",
            "-gryl",
            "-he",
            "-ho",
            "-hy",
            "-i +c",
            "-le",
            "-ly +c",
            "-mal",
            "-man",
            "-mac",
            "-me",
            "-mi",
            "-nu",
            "-neu +c",
            "-o +c",
            "-oe",
            "-a +c",
            "-ar",
            "-ph +v",
            "-pe",
            "-pte +c",
            "-pie",
            "-pro",
            "-pso",
            "-pyr",
            "-ra",
            "-re",
            "-si",
            "-sti",
            "-stre +c",
            "-te",
            "-thy +c",
            "-thys +v",
            "-tri",
            "-zo",
            "-la",
            "-or",
            "-va",
            "to",
            "de",
            "le",
            "op",
            "te",
            "ri",
            "lem",
            "bo",
            "le",
            "map",
            "bi",
            "phe",
            "par",
            "mer",
            "si",
            "cy",
            "gyy +c",
            "lo",
            "blat",
            "mi",
            "pte",
            "mo",
            "no",
            "so",
            "tie",
            "sos",
            "gio",
            "pi",
            "do",
            "pha",
            "pan",
            "co",
            "ga",
            "cro",
            "leu +c",
            "co",
            "ry",
            "tro",
            "phi",
            "ro",
            "na",
            "tho",
            "lan",
            "xip",
            "xy",
            "as",
            "thi",
            "chae",
            "a +c",
            "ra",
            "per",
            "tu",
            "pho",
            "bel",
            "na",
            "rio",
            "lic +c",
            "psi",
            "sa",
            "nu",
            "ne",
            "xip",
            "dec",
            "nes",
            "ge",
            "cho",
            "rha",
            "can",
            "bi",
            "gna",
            "the",
            "la",
            "xen -v",
            "+a -c",
            "+be -c",
            "+ra",
            "+dea",
            "+dae -v",
            "+ga",
            "+la",
            "+ta",
            "+ris",
            "+pha",
            "+pe -v",
            "+tha",
            "+rum -v",
            "+tor",
            "+tans",
            "+na -v",
            "+tus",
            "+cus",
            "+pus",
            "+s -v",
            "+e -c"
    };

    private final NameGenerator familyNameGenerator = new NameGenerator(syllables);

    @Inject
    public InsectNameGenerator() throws IOException
    {
        super(syllables);
    }


    public String compose()
    {
        return compose(Math.max(new Random().nextInt(6), 2));
    }


    @Override
    public String compose(int syllables)
    {
        val name = super.compose(syllables);

        val familySyllableCount = Math.max(new Random().nextInt(7), 3);
        val family = super.compose(familySyllableCount);

        return family + " " + name;
    }
}
